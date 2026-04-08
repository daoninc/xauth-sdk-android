package com.daon.fido.sdk.sample.kt.screens.push

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.fido.sdk.sample.kt.R
import com.daon.fido.sdk.sample.kt.util.getFidoExtensions
import com.daon.fido.sdk.sample.kt.util.getResourceString
import com.daon.sdk.authenticator.controller.BiometricController
import com.daon.sdk.authenticator.controller.OOTPController
import com.daon.sdk.authenticator.controller.PasscodeController
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.faceauthenticator.controller.FaceController
import com.daon.sdk.voiceauthenticator.controller.VoiceController
import com.daon.sdk.xauth.ChooseAuthenticatorListener
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

data class PushAuthenticationState(
    val inProgress: Boolean = false,
    val transactionId: String = "",
    val authenticationStarted: Boolean = false,
    val authenticationCompleted: Boolean = false,
    val completionMessage: String? = null,
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

sealed class PushAuthenticationUiEvent {
    data class ShowToast(val message: String) : PushAuthenticationUiEvent()

    data class NavigateBack(val message: String? = null) : PushAuthenticationUiEvent()

    data object NavigateToChooseAuthenticator : PushAuthenticationUiEvent()

    data object NavigateToTransactionConfirmation : PushAuthenticationUiEvent()
}

/** Manages the UI state and business logic for the Push Authentication screen. */
@HiltViewModel
class PushAuthenticationViewModel
@Inject
constructor(
    private val application: Application,
    private val service: IXUAFService,
    private val prefs: SharedPreferences,
) : AndroidViewModel(application) {

    private val tag = PushAuthenticationViewModel::class.java.simpleName

    private val _state = MutableStateFlow(PushAuthenticationState())
    val state: StateFlow<PushAuthenticationState> = _state

    private val _eventFlow = MutableSharedFlow<PushAuthenticationUiEvent?>()
    val eventFlow = _eventFlow.asSharedFlow()

    private suspend fun emitEvent(event: PushAuthenticationUiEvent) {
        _eventFlow.emit(event)
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
            viewModelScope.launch {
                emitEvent(PushAuthenticationUiEvent.NavigateToChooseAuthenticator)
            }
        }
    }

    private val transactionConfirmationListener = TransactionConfirmationListener {
        _state.update { currentState ->
            val txnContent = it.toTransactionContent(application)
            currentState.copy(
                transactionState =
                    currentState.transactionState.copy(
                        transactionConfirmationRequired = true,
                        transactionContent = txnContent,
                    )
            )
        }
    }

    fun startPushAuthentication(transactionId: String) {
        // Prevent duplicate calls - authentication can only be started once
        if (_state.value.authenticationStarted) {
            return
        }

        _state.update { currentState ->
            currentState.copy(
                inProgress = true,
                transactionId = transactionId,
                authenticationStarted = true,
            )
        }
        val ixuaf = IXUAF(this.application, this.service, getFidoExtensions(prefs))
        authentication = ixuaf.authentication()
        authentication.transactionConfirmationListener = transactionConfirmationListener

        viewModelScope.launch(Dispatchers.Default) {
            val parameters = ParameterBuilder().id(transactionId)

            prefs.getString("currentUser", null)?.let { parameters.username(it) }

            val response = authentication.start(parameters.build(), chooseAuthenticatorListener)
            if (response is Success) {
                _state.update { currentState ->
                    currentState.copy(
                        inProgress = false,
                        authenticationCompleted = true,
                        completionMessage = getResourceString(R.string.push_authentication_success),
                        authenticationState =
                            PushAuthenticationState.AuthenticationState(), // Reset auth state
                    )
                }
            } else if (response is Failure) {
                _state.update { currentState ->
                    currentState.copy(
                        inProgress = false,
                        authenticationCompleted = true,
                        completionMessage =
                            "Push authentication failed: ${response.errorCode} : ${response.errorMessage}",
                        authenticationState =
                            PushAuthenticationState.AuthenticationState(), // Reset auth state
                    )
                }
            }
        }
    }

    fun setInProgressFalse() {
        _state.update { currentState -> currentState.copy(inProgress = false) }
    }

    fun clearSelectedGroup() {
        _state.update { currentState ->
            currentState.copy(
                inProgress = false,
                authenticationState = currentState.authenticationState.copy(groupSelected = false),
            )
        }
    }

    fun selectAuthenticatorGroup(group: Group) {
        _state.update { currentState ->
            currentState.copy(
                authenticationState =
                    currentState.authenticationState.copy(group = group, groupSelected = true)
            )
        }
    }

    fun authenticateSilent() {
        try {
            _state.value.authenticationState.group?.let { group ->
                val silentController = group.getSilentController()
                silentController.authenticate()
            }
        } catch (e: Exception) {
            Logger.logError(tag, "authenticateSilent: ${e.message}")
        }
    }

    fun getPasscodeController(): PasscodeController? {
        return try {
            _state.value.authenticationState.group?.getPasscodeController()
        } catch (e: Exception) {
            Logger.logError(tag, "getPasscodeController: ${e.message}")
            null
        }
    }

    fun getFaceController(): FaceController? {
        return try {
            _state.value.authenticationState.group?.getFaceController()
        } catch (e: Exception) {
            Logger.logError(tag, "getFaceController: ${e.message}")
            null
        }
    }

    fun getVoiceController(): VoiceController? {
        return try {
            _state.value.authenticationState.group?.getVoiceController()
        } catch (e: Exception) {
            Logger.logError(tag, "getVoiceController: ${e.message}")
            null
        }
    }

    fun getFingerprintController(): BiometricController? {
        return try {
            _state.value.authenticationState.group?.getBiometricController()
        } catch (e: Exception) {
            Logger.logError(tag, "getFingerprintController: ${e.message}")
            null
        }
    }

    fun getOotpController(): OOTPController? {
        return try {
            _state.value.authenticationState.group?.getOOTPController()
        } catch (e: Exception) {
            Logger.logError(tag, "getOotpController: ${e.message}")
            null
        }
    }

    // Transaction confirmation methods

    /** Update the transaction state with the transaction confirmation result */
    fun setTransactionConfirmationResult(result: TransactionResult) {
        _state.update { currentState ->
            currentState.copy(
                transactionState =
                    currentState.transactionState.copy(transactionConfirmationResult = result)
            )
        }
    }

    /** Submit the transaction confirmation result to the SDK */
    fun submitDisplayTransactionResult(result: TransactionResult) {
        viewModelScope.launch(Dispatchers.Default) {
            clearTransactionConfirmationResult()
            authentication.submitDisplayTransactionResult(result)
        }
    }

    /** Reset the transaction data after confirmation screen is dismissed */
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

    /** Reset the transaction confirmation result */
    private fun clearTransactionConfirmationResult() {
        _state.update { currentState ->
            currentState.copy(
                transactionState =
                    currentState.transactionState.copy(transactionConfirmationResult = null)
            )
        }
    }
}
