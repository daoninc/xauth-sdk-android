package com.daon.fido.sdk.sample.kt.screens.intro

import android.app.Application
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.fido.sdk.sample.kt.settings.DevSettingsKeys
import com.daon.fido.sdk.sample.kt.util.context
import com.daon.fido.sdk.sample.kt.util.generateEmail
import com.daon.fido.sdk.sample.kt.util.getFidoExtensions
import com.daon.sdk.authenticator.controller.BiometricController
import com.daon.sdk.authenticator.controller.OOTPController
import com.daon.sdk.authenticator.controller.PasscodeController
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.faceauthenticator.controller.FaceController
import com.daon.sdk.voiceauthenticator.controller.VoiceController
import com.daon.sdk.xauth.AccountListListener
import com.daon.sdk.xauth.ChooseAuthenticatorListener
import com.daon.sdk.xauth.IXUAF
import com.daon.sdk.xauth.IXUAFService
import com.daon.sdk.xauth.UserLockWarningListener
import com.daon.sdk.xauth.core.Failure
import com.daon.sdk.xauth.core.Group
import com.daon.sdk.xauth.core.Policy
import com.daon.sdk.xauth.core.Success
import com.daon.sdk.xauth.model.AccountInfo
import com.daon.sdk.xauth.model.OOTPGenerationMode
import com.daon.sdk.xauth.util.ApplicationUtility
import com.daon.sdk.xauth.util.ParameterBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class representing the UI state of the Intro screen
data class IntroScreenState(
    val inProgress: Boolean = false,
    val username: String? = null,
    val sessionId: String? = null,
    val accountSelectionState: AccountSelectionState = AccountSelectionState(),
    val loginProcessState: LoginProcessState = LoginProcessState(),
    val ootpGenerationState: OOTPGenerationState = OOTPGenerationState(),
) {
    data class LoginProcessState(
        val policy: Policy? = null,
        val groupSelected: Boolean = false,
        val group: Group? = null,
    )

    data class AccountSelectionState(
        val accountListAvailable: Boolean = false,
        val accountArray: Array<AccountInfo> = emptyArray<AccountInfo>(),
        val accountSelected: Boolean = false,
        val selectedAccount: AccountInfo? = null,
    )

    data class OOTPGenerationState(
        val showResult: Boolean = false,
        val ootpValue: String? = null,
        val transactionData: String? = null,
    )
}

sealed class IntroUiEvent {
    data class ShowToast(val message: String) : IntroUiEvent()

    data object NavigateToHome : IntroUiEvent()

    data object NavigateToChooseAuthenticator : IntroUiEvent()
}

/*
 * IntroViewModel class manages the UI state and business logic for the
 * Intro screen.It handles FIDO initialization, account creation, authentication,
 * and log management.
 */
@HiltViewModel
class IntroViewModel
@Inject
constructor(
    private val application: Application,
    private val service: IXUAFService,
    private val prefs: SharedPreferences,
) : AndroidViewModel(application) {

    // Mutable state flow for the UI state
    private val _state = MutableStateFlow(IntroScreenState())
    // State flow for the UI state
    val state: StateFlow<IntroScreenState> = _state

    // Channel ensures each event is delivered exactly once, and buffers events when no collector
    // is active (e.g., during screen navigation)
    private val _eventChannel = Channel<IntroUiEvent>(capacity = Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private suspend fun emitEvent(event: IntroUiEvent) {
        _eventChannel.send(event)
    }

    private val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))

    private val authentication = ixuaf.authentication()

    private val chooseAuthenticatorListener = ChooseAuthenticatorListener {
        _state.update { currentState ->
            currentState.copy(loginProcessState = currentState.loginProcessState.copy(policy = it))
        }

        val isUpdate = it.isUpdate()
        if (isUpdate) {
            val groups = it.getGroups()
            groups.let { groupsArray ->
                if (groupsArray.isNotEmpty()) {
                    selectAuthenticatorGroup(groupsArray[0])
                }
            }
        } else {
            viewModelScope.launch { emitEvent(IntroUiEvent.NavigateToChooseAuthenticator) }
        }
    }

    private val accountListListener = AccountListListener {
        _state.update { currentState ->
            currentState.copy(
                accountSelectionState =
                    currentState.accountSelectionState.copy(
                        accountListAvailable = true,
                        accountArray = it,
                    )
            )
        }
    }

    // Start GPS locator
    fun startGps() {
        ixuaf.startLocator(null)
        gpsTimeoutCountdown.cancel()
        gpsTimeoutCountdown.start()
    }

    // Countdown timer for GPS timeout
    private val gpsTimeoutCountdown: CountDownTimer =
        object : CountDownTimer(60000, 60000) {
            override fun onTick(millisUntilFinished: Long) {
                // No action required.
            }

            override fun onFinish() {
                ixuaf.stopLocator()
            }
        }

    // Create a new account with generated email
    fun createAccount() {
        refreshExtensions()
        val usr = generateEmail()
        Logger.logDebug(tag = IntroViewModel::class.java.simpleName, "createNewAccount usr - $usr")
        _state.update { currentState -> currentState.copy(inProgress = true) }
        viewModelScope.launch {
            val params =
                ParameterBuilder()
                    .firstName("first name")
                    .lastName("last name")
                    .password("pp")
                    .build()

            when (val response = ixuaf.requestServiceAccess(usr, params)) {
                is Success -> {
                    val editor = prefs.edit()
                    editor.putString("currentUser", usr)
                    editor.apply()
                    _state.update { currentState ->
                        currentState.copy(
                            username = usr,
                            inProgress = false,
                            sessionId = response.sessionId,
                        )
                    }
                    emitEvent(IntroUiEvent.ShowToast("Account created successfully"))
                    emitEvent(IntroUiEvent.NavigateToHome)
                }

                is Failure -> {
                    _state.update { currentState ->
                        currentState.copy(username = usr, inProgress = false, sessionId = null)
                    }
                    emitEvent(
                        IntroUiEvent.ShowToast(
                            "Account creation failed : ${response.errorCode}, ${response.errorMessage}"
                        )
                    )
                }
            }
        }
    }

    private val userLockWarningListener = UserLockWarningListener { ->
        Logger.logDebug(
            IntroViewModel::class.java.simpleName,
            "User lock warning received - one attempt remaining until user account is blocked",
        )
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                    application,
                    "One attempt remaining until user account is blocked",
                    Toast.LENGTH_LONG,
                )
                .show()
        }
    }

    // Authenticate the user
    fun authenticate() {
        refreshExtensions()
        var accountsArray: List<String> = emptyList()
        val result = ixuaf.getAccounts()
        result.onSuccess { accounts -> accountsArray = accounts }
        _state.update { currentState -> currentState.copy(inProgress = true) }

        authentication.accountListListener = accountListListener
        authentication.userLockWarningListener = userLockWarningListener

        viewModelScope.launch(Dispatchers.Default) {
            val bundle = ParameterBuilder()
            if (accountsArray.isNotEmpty() && accountsArray.size == 1) {
                bundle.username(accountsArray[0])
            }
            when (
                val response = authentication.start(bundle.build(), chooseAuthenticatorListener)
            ) {
                is Success -> {
                    val username = response.email ?: prefs.getString("currentUser", null)
                    val sessionId = response.sessionId
                    val editor = prefs.edit()
                    editor.putString("currentUser", username)
                    editor.apply()
                    _state.update { currentState ->
                        currentState.copy(
                            inProgress = false,
                            username = username,
                            sessionId = sessionId,
                        )
                    }
                    emitEvent(IntroUiEvent.ShowToast("Authentication success."))
                    emitEvent(IntroUiEvent.NavigateToHome)
                }

                is Failure -> {
                    Logger.logDebug(
                        IntroViewModel::class.java.simpleName,
                        "IntroScreen start failure " +
                            "${response.errorCode} " +
                            "${response.errorMessage} + bundle -${response.params}",
                    )
                    _state.update { currentState ->
                        currentState.copy(
                            inProgress = false,
                            loginProcessState = IntroScreenState.LoginProcessState(),
                        )
                    }
                    emitEvent(
                        IntroUiEvent.ShowToast("Authentication failed: ${response.errorMessage}")
                    )
                }
            }
        }
    }

    // Perform silent authentication
    fun authenticateSilent() {
        try {
            _state.value.loginProcessState.group?.let { group ->
                val silentController = group.getSilentController()
                silentController.authenticate()
            }
        } catch (_: Exception) {}
    }

    fun getPasscodeController(): PasscodeController? {
        return try {
            _state.value.loginProcessState.group?.getPasscodeController()
        } catch (e: Exception) {
            Logger.logError(
                tag = IntroViewModel::class.java.simpleName,
                "getPasscodeController exception - ${e.message}",
            )
            null
        }
    }

    fun getFaceController(): FaceController? {
        return try {
            _state.value.loginProcessState.group?.getFaceController()
        } catch (e: Exception) {
            Logger.logError(
                tag = IntroViewModel::class.java.simpleName,
                "getFaceController exception - ${e.message}",
            )
            null
        }
    }

    fun getVoiceController(): VoiceController? {
        return try {
            state.value.loginProcessState.group?.getVoiceController()
        } catch (_: Exception) {
            null
        }
    }

    fun getFingerprintController(): BiometricController? {
        return try {
            _state.value.loginProcessState.group?.getBiometricController()
        } catch (e: Exception) {
            Logger.logError(
                tag = IntroViewModel::class.java.simpleName,
                "getFingerprintController exception - ${e.message}",
            )
            null
        }
    }

    fun getOotpController(): OOTPController? {
        return try {
            _state.value.loginProcessState.group?.getOOTPController()
        } catch (e: Exception) {
            Log.d("DAON", "getOotpController exception - ${e.message}")
            null
        }
    }

    // Generate OOTP (Offline One-Time Password)
    fun generateOOTP() {
        refreshExtensions()
        val currentUser = prefs.getString("currentUser", null)
        if (currentUser.isNullOrEmpty()) {
            viewModelScope.launch {
                emitEvent(
                    IntroUiEvent.ShowToast(
                        "No registered user found. Please create an account first."
                    )
                )
            }
            return
        }

        _state.update { currentState -> currentState.copy(inProgress = true) }

        val ootp = ixuaf.ootp()
        ootp.accountListListener = AccountListListener { accounts ->
            _state.update { currentState ->
                currentState.copy(
                    accountSelectionState =
                        currentState.accountSelectionState.copy(
                            accountListAvailable = true,
                            accountArray = accounts,
                        )
                )
            }
        }

        viewModelScope.launch {
            val params =
                ParameterBuilder()
                    .username(currentUser)
                    .appId(ApplicationUtility.getAppID() ?: "")
                    .build()

            when (
                val response =
                    ootp.generate(
                        params,
                        ootpChooseAuthenticatorListener,
                        OOTPGenerationMode.valueOf(
                            prefs.getString(DevSettingsKeys.OOTP_MODE, null) ?: "IdentifyWithOTP"
                        ),
                    )
            ) {
                is Success -> {
                    _state.update { currentState ->
                        currentState.copy(
                            inProgress = false,
                            ootpGenerationState =
                                currentState.ootpGenerationState.copy(
                                    showResult = true,
                                    ootpValue = response.ootpValue,
                                    transactionData = response.ootpTransactionData,
                                ),
                        )
                    }
                }

                is Failure -> {
                    _state.update { currentState -> currentState.copy(inProgress = false) }
                    emitEvent(
                        IntroUiEvent.ShowToast(
                            "OOTP generation failed: ${response.errorCode}, ${response.errorMessage}"
                        )
                    )
                }
            }
        }
    }

    private val ootpChooseAuthenticatorListener = ChooseAuthenticatorListener { policy ->
        val isUpdate = policy.isUpdate()
        _state.update { currentState ->
            currentState.copy(
                loginProcessState = currentState.loginProcessState.copy(policy = policy)
            )
        }

        if (isUpdate) {
            val groups = policy.getGroups()
            groups.let { groupsArray ->
                if (groupsArray.isNotEmpty()) {
                    selectAuthenticatorGroup(groupsArray[0])
                }
            }
        } else {
            viewModelScope.launch { emitEvent(IntroUiEvent.NavigateToChooseAuthenticator) }
        }
    }

    fun dismissOOTPResult() {
        _state.update { currentState ->
            currentState.copy(ootpGenerationState = IntroScreenState.OOTPGenerationState())
        }
    }

    // Reset the UI state
    fun clearUiState() {
        _state.value = IntroScreenState()
    }

    fun setInProgressFalse() {
        _state.update { currentState -> currentState.copy(inProgress = false) }
    }

    // Update the selected authenticator group
    fun selectAuthenticatorGroup(group: Group) {
        _state.update { currentState ->
            currentState.copy(
                loginProcessState =
                    currentState.loginProcessState.copy(groupSelected = true, group = group)
            )
        }
    }

    // Update the selected authenticator
    fun selectAccount(account: AccountInfo) {
        _state.update { currentState ->
            currentState.copy(
                accountSelectionState =
                    currentState.accountSelectionState.copy(
                        selectedAccount = account,
                        accountSelected = true,
                        accountListAvailable = false,
                        accountArray = emptyArray<AccountInfo>(),
                    )
            )
        }
    }

    fun submitSelectedAccount() {
        state.value.accountSelectionState.selectedAccount?.let {
            authentication.submitUserSelectedAccount(it)
        }
        _state.update { currentState ->
            currentState.copy(
                accountSelectionState =
                    currentState.accountSelectionState.copy(accountSelected = false)
            )
        }
    }

    fun clearAccountList() {
        _state.update { currentState ->
            currentState.copy(
                accountSelectionState =
                    currentState.accountSelectionState.copy(
                        accountListAvailable = false,
                        accountArray = emptyArray<AccountInfo>(),
                    )
            )
        }
    }

    // Deselect the selected authenticator
    fun clearSelectedGroup() {
        _state.update { currentState ->
            currentState.copy(
                loginProcessState = currentState.loginProcessState.copy(groupSelected = false)
            )
        }
    }

    fun configureLogging() {
        Logger.configure(application, true, Logger.LogLevel.VERBOSE, true)
        rotateLogs()
    }

    // Rotate logs if file size is greater than 5KB
    private fun rotateLogs() {
        if (isFileSizeGreaterThan5KB()) {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS-dd-MM-yyyy", Locale.getDefault())
            val currentDate = sdf.format(Date())
            Logger.rotate(context, "$currentDate-fido-ixa.log")
            deleteLogs()
        }
    }

    // Check if file size is greater than 5KB
    private fun isFileSizeGreaterThan5KB(): Boolean {
        val file = File(context.getExternalFilesDir(null), "daon-ixa.log")
        return if (file.exists()) {
            val fileSizeInBytes = file.length()
            val fileSizeInKB = fileSizeInBytes / 1024
            fileSizeInKB > 5
        } else {
            false
        }
    }

    // Delete the oldest logs if more than 5 files are present
    private fun deleteLogs() {
        val directory = context.getExternalFilesDir(null)
        val logFileNamePattern =
            Pattern.compile("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}-\\d{2}-\\d{2}-\\d{4}-fido-ixa\\.log")
        val files = directory?.listFiles()
        val logFiles =
            files
                ?.filter { logFileNamePattern.matcher(it.name).matches() }
                ?.sortedBy { it.lastModified() }
        if (logFiles != null && logFiles.size > 5) {
            for (i in 0 until logFiles.size - 5) {
                Logger.delete(context, logFiles[i].name)
            }
        }
    }

    private fun refreshExtensions() {
        ixuaf.extensions = getFidoExtensions(prefs)
    }
}
