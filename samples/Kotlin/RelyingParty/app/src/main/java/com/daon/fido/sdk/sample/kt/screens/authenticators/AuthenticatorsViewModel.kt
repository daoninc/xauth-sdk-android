package com.daon.fido.sdk.sample.kt.screens.authenticators

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.fido.sdk.sample.kt.util.getFidoExtensions
import com.daon.sdk.authenticator.controller.BiometricController
import com.daon.sdk.authenticator.controller.OOTPController
import com.daon.sdk.authenticator.controller.PasscodeController
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.faceauthenticator.controller.FaceController
import com.daon.sdk.voiceauthenticator.controller.VoiceController
import com.daon.sdk.xauth.ChooseAuthenticatorListener
import com.daon.sdk.xauth.IXUAF
import com.daon.sdk.xauth.IXUAFService
import com.daon.sdk.xauth.core.Failure
import com.daon.sdk.xauth.core.Group
import com.daon.sdk.xauth.core.Policy
import com.daon.sdk.xauth.core.Success
import com.daon.sdk.xauth.model.Authenticator
import com.daon.sdk.xauth.util.ApplicationUtility
import com.daon.sdk.xauth.util.ParameterBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class to hold the state of the Authenticators screen.
data class AuthenticatorState(
    val inProgress: Boolean = false,
    val sessionId: String = "",
    val discoverList: List<Authenticator> = emptyList(),
    val registrationState: RegistrationState = RegistrationState(),
    val selectedIndex: Int = -1,
    val authToDeregister: Authenticator? = null,
)

data class RegistrationState(
    val policy: Policy? = null,
    val groupSelected: Boolean = false,
    val group: Group? = null,
)

sealed class AuthenticatorsUiEvent {
    data class ShowToast(val message: String) : AuthenticatorsUiEvent()

    data object NavigateToChooseAuthenticator : AuthenticatorsUiEvent()
}

/*
 * ViewModel for the Authenticators screen.
 * It handles FIDO registration and deregistration of authenticators .
 */
@HiltViewModel
class AuthenticatorsViewModel
@Inject
constructor(
    private val application: Application,
    private val service: IXUAFService,
    private val prefs: SharedPreferences,
) : AndroidViewModel(application) {

    private val tag = AuthenticatorsViewModel::class.java.simpleName
    private val _state = MutableStateFlow(AuthenticatorState())
    val state: StateFlow<AuthenticatorState> = _state

    private val _eventFlow = MutableSharedFlow<AuthenticatorsUiEvent?>()
    val eventFlow = _eventFlow.asSharedFlow()

    private suspend fun emitEvent(event: AuthenticatorsUiEvent) {
        _eventFlow.emit(event)
    }

    private val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))
    private val registration = ixuaf.registration()

    private val chooseAuthenticatorListener = ChooseAuthenticatorListener {
        setInProgress(false)
        _state.update { currentState ->
            currentState.copy(registrationState = currentState.registrationState.copy(policy = it))
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
            viewModelScope.launch { emitEvent(AuthenticatorsUiEvent.NavigateToChooseAuthenticator) }
        }
    }

    fun onStart() {
        discover()
    }

    // Discover available authenticators.
    private fun discover() {
        val username = prefs.getString("currentUser", null).toString()
        viewModelScope.launch(Dispatchers.Default) {
            val appId = ApplicationUtility.getAppID()
            val result = ixuaf.discover()
            result
                .onSuccess { discover ->
                    val resultArray = mutableStateListOf<Authenticator>()
                    discover.availableAuthenticators.forEach { auth ->
                        auth.aaid?.let { aaid ->
                            val res = ixuaf.isRegistered(aaid, username, appId)
                            if (res.isSuccess && res.getOrNull() == true) {
                                resultArray.add(auth)
                            }
                        }
                    }
                    _state.update { currentState -> currentState.copy(discoverList = resultArray) }
                }
                .onFailure {
                    // Handle failure here
                }
        }
    }

    // Registration the selected authenticator.
    fun register() {
        setInProgress(true)

        viewModelScope.launch(Dispatchers.Default) {
            val bundle = ParameterBuilder().sessionId(_state.value.sessionId)
            prefs.getString("currentUser", null)?.let { bundle.username(it) }

            /*
            //Updating the extensions

            val extensions = ExtensionBuilder()
                .silentFingerprintRegistration(false)
                .build()
            fido.extensions.putAll(extensions)

            //OR

            fido.extensions.putString("com.daon.finger.silent", "false")

            //OR

            val extensions = ExtensionBuilder()
                .silentFingerprintRegistration(false)
                .build()
            bundle.putAll(extensions)
            */

            when (val response = registration.start(bundle.build(), chooseAuthenticatorListener)) {
                is Success -> {
                    emitEvent(AuthenticatorsUiEvent.ShowToast("Registration success"))
                    setInProgress(false)
                    discover()
                }

                is Failure -> {
                    Logger.logDebug(
                        tag,
                        "fido register failure: ${response.errorCode}, Message: ${response.errorMessage}",
                    )
                    setInProgress(false)
                    emitEvent(
                        AuthenticatorsUiEvent.ShowToast(
                            "Registration failed : ${response.errorCode}, Message: ${response.errorMessage  }"
                        )
                    )
                }
            }
        }
    }

    // Reset the selected authenticator.
    fun deregister(auth: Authenticator) {
        setInProgress(true)
        viewModelScope.launch(Dispatchers.Default) {
            val deregistration = ixuaf.deregistration()
            val username = prefs.getString("currentUser", null).toString()
            val params = ParameterBuilder().sessionId(_state.value.sessionId).build()

            auth.aaid?.let { aaid ->
                when (val response = deregistration.start(aaid, username, params)) {
                    is Success -> {
                        // Handle successful deregistration.
                        Logger.logDebug(tag, "fido deregister success")
                        markAuthenticatorForDeregistration(null, -1)
                        setInProgress(false)
                        emitEvent(AuthenticatorsUiEvent.ShowToast("Deregistration success"))
                        discover()
                    }

                    is Failure -> {
                        // Handle deregistration failure.
                        markAuthenticatorForDeregistration(null, -1)
                        Logger.logDebug(tag, "fido deregister failure")
                        setInProgress(false)
                        emitEvent(
                            AuthenticatorsUiEvent.ShowToast(
                                "Deregistration failed: ${response.errorCode}, Message: ${response.errorMessage}"
                            )
                        )
                    }
                }
            }
        }
    }

    fun setInProgress(value: Boolean) {
        _state.update { currentAuthState -> currentAuthState.copy(inProgress = value) }
    }

    // Update the user selected authenticator group
    fun selectAuthenticatorGroup(group: Group) {
        _state.update { currentState ->
            currentState.copy(
                registrationState =
                    currentState.registrationState.copy(group = group, groupSelected = true)
            )
        }
    }

    // Update authState with the authenticator to deregister.
    fun markAuthenticatorForDeregistration(auth: Authenticator?, index: Int) {
        _state.update { currentState ->
            currentState.copy(authToDeregister = auth, selectedIndex = index)
        }
    }

    // Reset the selected authenticator.
    fun clearSelectedGroup() {
        _state.update { currentState ->
            currentState.copy(
                registrationState = currentState.registrationState.copy(groupSelected = false)
            )
        }
    }

    // Perform silent registration.
    fun registerSilent() {
        try {
            _state.value.registrationState.group?.let { group ->
                val silentController = group.getSilentController()
                silentController.register()
            }
        } catch (_: Exception) {}
    }

    fun getPasscodeController(): PasscodeController? {
        return try {
            _state.value.registrationState.group?.getPasscodeController()
        } catch (_: Exception) {
            null
        }
    }

    fun getFaceController(): FaceController? {
        return try {
            _state.value.registrationState.group?.getFaceController()
        } catch (_: Exception) {
            null
        }
    }

    fun getVoiceController(): VoiceController? {
        return try {
            _state.value.registrationState.group?.getVoiceController()
        } catch (_: Exception) {
            null
        }
    }

    fun getFingerprintController(): BiometricController? {
        return try {
            _state.value.registrationState.group?.getBiometricController()
        } catch (_: Exception) {
            null
        }
    }

    fun getOotpController(): OOTPController? {
        return try {
            _state.value.registrationState.group?.getOOTPController()
        } catch (e: Exception) {
            null
        }
    }

    fun setSessionId(sessionId: String) {
        _state.update { currentState -> currentState.copy(sessionId = sessionId) }
    }
}
