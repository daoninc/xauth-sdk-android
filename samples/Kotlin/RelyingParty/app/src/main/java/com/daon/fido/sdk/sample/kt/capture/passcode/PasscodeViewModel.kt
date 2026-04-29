package com.daon.fido.sdk.sample.kt.capture.passcode

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.sdk.authenticator.controller.CaptureController
import com.daon.sdk.authenticator.controller.PasscodeController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PasscodeUiEvent {
    data class ShowToast(val message: String) : PasscodeUiEvent()

    data object NavigateUp : PasscodeUiEvent()

    data object EnableRetry : PasscodeUiEvent()
}

/**
 * ViewModel class to handle passcode registration, authentication and re-enrollment.
 *
 * @param application The application context.
 * @param prefs The SharedPreferences instance.
 */
@HiltViewModel
class PasscodeViewModel
@Inject
constructor(application: Application, private val prefs: SharedPreferences) :
    AndroidViewModel(application) {

    lateinit var controller: PasscodeController

    private val _eventFlow = MutableSharedFlow<PasscodeUiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val _inProgress = MutableStateFlow(false)
    val inProgress = _inProgress.asStateFlow()

    private suspend fun emitEvent(event: PasscodeUiEvent) {
        _eventFlow.emit(event)
    }

    /** Start the capture process. */
    fun onStart(passcodeController: PasscodeController) {
        controller = passcodeController
        controller.startCapture()
    }

    fun onStop() {}

    /**
     * Registration the passcode.
     *
     * @param value The passcode to be registered.
     */
    fun register(value: String) {
        if (_inProgress.value) return
        _inProgress.value = true
        viewModelScope.launch {
            try {
                controller.register(value.toCharArray()).collect { event ->
                    handleRegistrationEvent(event)
                }
            } catch (e: Exception) {
                _inProgress.value = false
                showToastAndNavigateUp("Error: ${e.message}")
            }
        }
    }

    /**
     * Authenticate the passcode.
     *
     * @param value The passcode to be authenticated.
     */
    fun authenticate(value: String) {
        if (_inProgress.value) return
        _inProgress.value = true
        viewModelScope.launch {
            controller.authenticate(value.toCharArray()).collect { event ->
                handleAuthenticationEvent(event)
            }
        }
    }

    /**
     * Verify and re-enroll the passcode.
     *
     * @param currentPasscode The current passcode.
     * @param newPasscode The new passcode.
     */
    fun verifyAndReenrol(currentPasscode: String, newPasscode: String) {
        if (_inProgress.value) return
        _inProgress.value = true
        viewModelScope.launch {
            controller
                .verifyAndReenroll(currentPasscode.toCharArray(), newPasscode.toCharArray())
                .collect { event -> handleAuthenticationEvent(event) }
        }
    }

    /**
     * Get the retries remaining message.
     *
     * @param result The CaptureCompleteResult.
     * @return The retries remaining message.
     */
    private fun getRetriesRemainingMessage(retries: Int): String {
        return when {
            retries == 1 -> "1 retry remaining"
            retries > 1 -> "$retries retries remaining"
            else -> "Please try again later"
        }
    }

    private suspend fun handleRegistrationEvent(event: CaptureController.RegistrationEvent) {
        _inProgress.value = false
        when (event) {
            is CaptureController.RegistrationEvent.Success -> {
                navigateUp()
            }

            is CaptureController.RegistrationEvent.Failure.Validation -> {
                val errorMessage =
                    event.error.message?.let {
                        "$it\n${getRetriesRemainingMessage(event.retriesRemaining)}"
                    } ?: getRetriesRemainingMessage(event.retriesRemaining)
                enableRetryAndShowToast(errorMessage)
            }

            is CaptureController.RegistrationEvent.Failure.Fatal -> {
                showToastAndNavigateUp(
                    "Registration failed: ${event.error?.message ?: "An unexpected error occurred."}"
                )
            }
        }
    }

    private suspend fun handleAuthenticationEvent(event: CaptureController.AuthenticationEvent) {
        _inProgress.value = false
        when (event) {
            is CaptureController.AuthenticationEvent.Success -> {
                navigateUp()
            }

            is CaptureController.AuthenticationEvent.Failure.Validation -> {
                val errorMessage =
                    event.error.message?.let {
                        "$it\n${getRetriesRemainingMessage(event.retriesRemaining)}"
                    } ?: getRetriesRemainingMessage(event.retriesRemaining)
                enableRetryAndShowToast(errorMessage)
            }

            is CaptureController.AuthenticationEvent.Failure.Fatal -> {
                if (event.error != null) {
                    showToastAndNavigateUp("Authentication failed: ${event.error?.message}")
                } else {
                    navigateUp()
                }
            }
        }
    }

    private suspend fun navigateUp() {
        emitEvent(PasscodeUiEvent.NavigateUp)
    }

    private suspend fun enableRetryAndShowToast(message: String?) {
        emitEvents(PasscodeUiEvent.EnableRetry, PasscodeUiEvent.ShowToast(message ?: ""))
    }

    private suspend fun showToast(message: String?) {
        emitEvent(PasscodeUiEvent.ShowToast(message ?: ""))
    }

    private suspend fun showToastAndNavigateUp(message: String?) {
        emitEvents(PasscodeUiEvent.ShowToast(message ?: ""), PasscodeUiEvent.NavigateUp)
    }

    private suspend fun emitEvents(vararg events: PasscodeUiEvent) {
        events.forEach { emitEvent(it) }
    }
}
