package com.daon.fido.sdk.sample.kt.screens.home

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.fido.sdk.sample.kt.R
import com.daon.fido.sdk.sample.kt.settings.DevSettingsKeys
import com.daon.fido.sdk.sample.kt.util.context
import com.daon.fido.sdk.sample.kt.util.getFidoExtensions
import com.daon.fido.sdk.sample.kt.util.getResourceString
import com.daon.sdk.authenticator.controller.BiometricController
import com.daon.sdk.authenticator.controller.OOTPController
import com.daon.sdk.authenticator.controller.PasscodeController
import com.daon.sdk.authenticator.controller.SilentController
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.faceauthenticator.controller.FaceController
import com.daon.sdk.voiceauthenticator.controller.VoiceController
import com.daon.sdk.xauth.ChooseAuthenticatorListener
import com.daon.sdk.xauth.ConfirmationOTPListener
import com.daon.sdk.xauth.IXUAF
import com.daon.sdk.xauth.IXUAFService
import com.daon.sdk.xauth.TransactionConfirmationListener
import com.daon.sdk.xauth.auth.Authentication
import com.daon.sdk.xauth.core.Failure
import com.daon.sdk.xauth.core.Group
import com.daon.sdk.xauth.core.Policy
import com.daon.sdk.xauth.core.Success
import com.daon.sdk.xauth.model.TransactionContent
import com.daon.sdk.xauth.model.TransactionResult
import com.daon.sdk.xauth.transaction.toTransactionContent
import com.daon.sdk.xauth.util.ApplicationUtility
import com.daon.sdk.xauth.util.AuthenticatorIds.SILENT
import com.daon.sdk.xauth.util.ParameterBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class HomeScreenState(
    val inProgress: Boolean = false,
    val sessionId: String = "",
    val authenticationState: AuthenticationState = AuthenticationState(),
    val transactionState: TransactionState = TransactionState(),
) {
    data class AuthenticationState(
        val policy: Policy? = null,
        val groupSelected: Boolean = false,
        val group: Group? = null,
    )

    data class TransactionState(
        val transactionConfirmationRequired: Boolean = false,
        val transactionContent: TransactionContent? = null,
        val transactionConfirmationResult: TransactionResult? = null,
    )
}

data class SilentAuthState(val inProgress: Boolean = false)

sealed class HomeUiEvent {
    data class ShowToast(val message: String) : HomeUiEvent()

    data object NavigateBackToIntro : HomeUiEvent()

    data object NavigateToChooseAuthenticator : HomeUiEvent()
}

/** Manages the UI state and business logic for the Home screen. */
@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val application: Application,
    private val service: IXUAFService,
    private val prefs: SharedPreferences,
) : AndroidViewModel(application) {

    private val tag = HomeViewModel::class.java.simpleName
    // Mutable state flow for the transaction state
    private val _state = MutableStateFlow(HomeScreenState())

    // Public state flow for the transaction state
    val state: StateFlow<HomeScreenState> = _state

    private val _silentAuthState = MutableStateFlow(SilentAuthState())
    val silentAuthState: StateFlow<SilentAuthState> = _silentAuthState

    // Channel ensures each event is delivered exactly once, and buffers events when no collector
    // is active (e.g., during screen navigation)
    private val _eventChannel = Channel<HomeUiEvent>(capacity = Channel.BUFFERED)
    val eventFlow = _eventChannel.receiveAsFlow()

    private suspend fun emitEvent(event: HomeUiEvent) {
        _eventChannel.send(event)
    }

    private lateinit var authentication: Authentication

    private val chooseAuthenticatorListener = ChooseAuthenticatorListener {
        _state.update { currentState ->
            currentState.copy(
                authenticationState = currentState.authenticationState.copy(policy = it)
            )
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
            viewModelScope.launch { emitEvent(HomeUiEvent.NavigateToChooseAuthenticator) }
        }
    }

    private val transactionConfirmationListener = TransactionConfirmationListener {
        _state.update { currentState ->
            val txnContent = it.toTransactionContent(context)
            // Update the transaction state with the transaction content
            currentState.copy(
                transactionState =
                    currentState.transactionState.copy(
                        transactionConfirmationRequired = true,
                        transactionContent = txnContent,
                    )
            )
        }
    }

    private val confirmationOTPListener = ConfirmationOTPListener {
        viewModelScope.launch { emitEvent(HomeUiEvent.ShowToast("Your One-Time Password - $it")) }
    }

    // Authenticate the user
    fun authenticate(isSingleShot: Boolean) {
        _state.update { currentState -> currentState.copy(inProgress = true) }
        val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))
        authentication = ixuaf.authentication()
        authentication.transactionConfirmationListener = transactionConfirmationListener
        authentication.confirmationOTPListener = confirmationOTPListener
        viewModelScope.launch(Dispatchers.Default) {
            val parameters =
                ParameterBuilder()
                    .singleShot(isSingleShot)
                    .confirmationOTP(prefs.getBoolean(DevSettingsKeys.CONFIRMATION_OTP, false))
                    .transactionContentType("text/plain")
                    .transactionContent(getResourceString(R.string.transaction_text_content))
                    .sessionId(state.value.sessionId)
            prefs.getString("currentUser", null)?.let { parameters.username(it) }
            ApplicationUtility.getAppID()?.let { parameters.appId(it) }

            val response = authentication.start(parameters.build(), chooseAuthenticatorListener)
            if (response is Success) {
                _state.update { currentState -> currentState.copy(inProgress = false) }
                emitEvent(
                    HomeUiEvent.ShowToast(
                        getResourceString(R.string.transaction_validation_success)
                    )
                )
            } else if (response is Failure) {
                _state.update { currentState -> currentState.copy(inProgress = false) }
                emitEvent(
                    HomeUiEvent.ShowToast(
                        "Authentication failed: ${response.errorCode} :  ${response.errorMessage}"
                    )
                )
            }
        }
    }

    // Archive the user and remove all Fido SDK data from the device
    fun delete() {
        deleteUser()
    }

    // Archive the current user
    private fun deleteUser() {
        val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))
        val username = prefs.getString("currentUser", null)
        val bundle = ParameterBuilder().sessionId(_state.value.sessionId).build()
        viewModelScope.launch {
            when (ixuaf.deleteUser(username, bundle)) {
                is Success -> {
                    Logger.logDebug(
                        tag = HomeViewModel::class.java.simpleName,
                        "Delete User Success",
                    )
                    resetFido()
                }

                is Failure -> {
                    Logger.logError(
                        tag = HomeViewModel::class.java.simpleName,
                        "Delete User Failure",
                    )
                }
            }
        }
    }

    // Remove all Fido SDK data from the device
    private fun resetFido() {
        val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))
        val username = prefs.getString("currentUser", null)
        viewModelScope.launch {
            when (val response = ixuaf.reset(ApplicationUtility.getAppID(), username)) {
                is Success -> {
                    emitEvent(HomeUiEvent.ShowToast("Reset success."))
                    emitEvent(HomeUiEvent.NavigateBackToIntro)
                }

                is Failure -> {
                    emitEvent(
                        HomeUiEvent.ShowToast(
                            "Reset failed: ${response.errorCode} :  ${response.errorMessage}"
                        )
                    )
                }
            }
        }
    }

    // Logout the user
    fun doLogout() {
        val bundle = ParameterBuilder().sessionId(_state.value.sessionId).build()
        val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))
        viewModelScope.launch {
            when (ixuaf.revokeServiceAccess(bundle)) {
                is Success -> {
                    Logger.logDebug(tag = HomeViewModel::class.java.simpleName, "Logout Success")
                }

                is Failure -> {
                    Logger.logError(tag = HomeViewModel::class.java.simpleName, "Logout Failure")
                }
            }
        }
    }

    fun setInProgressFalse() {
        _state.update { currentState -> currentState.copy(inProgress = false) }
    }

    // Reset the authSelected flag
    fun clearSelectedGroup() {
        _state.update { currentState ->
            currentState.copy(
                inProgress = false,
                authenticationState = currentState.authenticationState.copy(groupSelected = false),
            )
        }
    }

    // Update the selected authenticator
    fun selectAuthenticatorGroup(group: Group) {
        _state.update { currentState ->
            currentState.copy(
                authenticationState =
                    currentState.authenticationState.copy(group = group, groupSelected = true)
            )
        }
    }

    // Update the transaction state with the transaction confirmation result
    fun setTransactionConfirmationResult(result: TransactionResult) {
        _state.update { currentState ->
            currentState.copy(
                transactionState =
                    currentState.transactionState.copy(transactionConfirmationResult = result)
            )
        }
    }

    // Submit the transaction confirmation result
    fun submitDisplayTransactionResult(result: TransactionResult) {
        viewModelScope.launch(Dispatchers.Default) {
            clearTransactionConfirmationResult()
            authentication.submitDisplayTransactionResult(result)
        }
    }

    // Reset the transaction data
    fun clearTransactionData() {
        _state.update { currentState ->
            currentState.copy(
                transactionState =
                    currentState.transactionState.copy(
                        transactionConfirmationRequired = false,
                        transactionContent = null,
                    )
            )
        }
    }

    // Reset the transaction confirmation result
    private fun clearTransactionConfirmationResult() {
        _state.update { currentState ->
            currentState.copy(
                transactionState =
                    currentState.transactionState.copy(transactionConfirmationResult = null)
            )
        }
    }

    fun setSessionId(sessionId: String) {
        _state.update { it.copy(sessionId = sessionId) }
    }

    // Perform silent authentication
    fun authenticateSilent() {
        try {
            _state.value.authenticationState.group?.let { group ->
                val silentController = group.getSilentController()
                silentController.authenticate()
            }
        } catch (e: Exception) {}
    }

    fun getPasscodeController(): PasscodeController? {
        return try {
            _state.value.authenticationState.group?.getPasscodeController()
        } catch (e: Exception) {
            Logger.logError(
                HomeViewModel::class.java.simpleName,
                "getPasscodeController: ${e.message}",
            )
            null
        }
    }

    fun getFaceController(): FaceController? {
        return try {
            _state.value.authenticationState.group?.getFaceController()
        } catch (e: Exception) {
            Logger.logError(HomeViewModel::class.java.simpleName, "getFaceController: ${e.message}")
            null
        }
    }

    fun getVoiceController(): VoiceController? {
        return try {
            _state.value.authenticationState.group?.getVoiceController()
        } catch (e: Exception) {
            Logger.logError(
                HomeViewModel::class.java.simpleName,
                "getVoiceController: ${e.message}",
            )
            null
        }
    }

    fun getFingerprintController(): BiometricController? {
        return try {
            _state.value.authenticationState.group?.getBiometricController()
        } catch (e: Exception) {
            Logger.logError(
                HomeViewModel::class.java.simpleName,
                "getFingerprintController: ${e.message}",
            )
            null
        }
    }

    fun getOotpController(): OOTPController? {
        return try {
            _state.value.authenticationState.group?.getOOTPController()
        } catch (e: Exception) {
            Logger.logError(
                tag = HomeViewModel::class.java.simpleName,
                "getOotpController: ${e.message}",
            )
            null
        }
    }

    private var silentDemoJob: Job? = null

    fun startSilentAuthInBackground() {
        if (_silentAuthState.value.inProgress) {
            return
        }

        // Create a new IXUAF instance for authentication
        val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))

        silentDemoJob =
            CoroutineScope(Dispatchers.Default).launch {
                _silentAuthState.update { it.copy(inProgress = true) }

                try {
                    while (isActive && isSilentRegistered(ixuaf)) {
                        Logger.logDebug(
                            tag = HomeViewModel::class.java.simpleName,
                            "Silent Auth registered",
                        )
                        authenticateSilentConcurrently()
                        delay(5000) // Sleep for 5 seconds before the next silent authentication
                    }

                    if (!isSilentRegistered(ixuaf)) {
                        Logger.logDebug(
                            tag = HomeViewModel::class.java.simpleName,
                            "Silent Auth Not Registered",
                        )
                    }
                } finally {
                    _silentAuthState.update { it.copy(inProgress = false) }
                }
            }
    }

    fun stopSilentAuthInBackground() {
        _silentAuthState.update { currentState -> currentState.copy(inProgress = false) }

        silentDemoJob?.cancel()
        silentDemoJob = null
    }

    fun authenticateSilentConcurrently() {

        val ixuaf = IXUAF(application, service, getFidoExtensions(prefs))
        // Launch a parent coroutine on the Default dispatcher for concurrent execution
        viewModelScope.launch(Dispatchers.Default) {
            // Create 5 child coroutines, each performing a silent authentication
            val jobs =
                (1..5).map { index ->
                    launch {
                        val chooseAuthenticatorListenerSilent =
                            ChooseAuthenticatorListener { policy ->
                                val groups = policy.getGroups()
                                val silentGroup =
                                    groups.find { it.getAuthenticator().aaid == SILENT }

                                if (silentGroup != null) {
                                    val controller =
                                        silentGroup.getSilentController() as SilentController
                                    controller.authenticate()
                                }
                            }

                        val bundle = ParameterBuilder().sessionId(state.value.sessionId)
                        prefs.getString("currentUser", null)?.let { bundle.username(it) }

                        val response =
                            ixuaf
                                .authentication()
                                .start(bundle.build(), chooseAuthenticatorListenerSilent)

                        when (response) {
                            is Success -> {
                                Logger.logDebug(tag, "Silent Auth $index Success")
                            }

                            is Failure -> {
                                Logger.logDebug(
                                    tag,
                                    "Silent Auth $index Failure: ${response.errorCode} :  ${response.errorMessage}",
                                )
                            }
                        }
                    }
                }

            jobs.joinAll()
        }
    }

    suspend fun isSilentRegistered(ixuaf: IXUAF): Boolean {
        val username = prefs.getString("currentUser", null) ?: ""
        val appID = ApplicationUtility.getAppID()
        val result = ixuaf.isRegistered(SILENT, username, appID)
        return result.getOrElse { false }
    }
}
