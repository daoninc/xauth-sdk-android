package com.daon.fido.sdk.sample.kt.capture.ootp

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.sdk.authenticator.controller.CaptureController
import com.daon.sdk.authenticator.controller.OOTPController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class OOTPUiEvent {
    data class ShowToast(val message: String) : OOTPUiEvent()

    data object NavigateUp : OOTPUiEvent()

    data object EnableRetry : OOTPUiEvent()
}

/**
 * ViewModel class to handle OOTP registration and authentication.
 *
 * @param application The application context.
 * @param prefs The SharedPreferences instance.
 */
@HiltViewModel
class OOTPViewModel
@Inject
constructor(application: Application, private val prefs: SharedPreferences) :
    AndroidViewModel(application) {

    lateinit var controller: OOTPController

    // replay=1 ensures new subscribers receive the last event (handles race between collector setup
    // and emit)
    private val _eventFlow = MutableSharedFlow<OOTPUiEvent>(replay = 1)
    val eventFlow: SharedFlow<OOTPUiEvent> = _eventFlow.asSharedFlow()

    private fun emitEvent(event: OOTPUiEvent) {
        Log.d(
            "OOTPViewModel",
            "emitEvent($event) - subscriptionCount=${_eventFlow.subscriptionCount.value}",
        )
        _eventFlow.tryEmit(event)
    }

    /** Start the capture process. */
    fun onStart(ootpController: OOTPController) {
        controller = ootpController
        controller.startCapture()
    }

    fun onStop() {}

    /** Register OOTP (silent operation - no user input required) */
    fun register() {
        viewModelScope.launch {
            try {
                controller.register().collect { event -> handleRegistrationEvent(event) }
            } catch (e: Exception) {
                showToastAndNavigateUp("Error: ${e.message}")
            }
        }
    }

    /**
     * Authenticate with OOTP
     *
     * @param transactionData optional transaction data from QR code or manual entry
     */
    fun authenticate(transactionData: String? = null) {
        viewModelScope.launch {
            try {
                controller.authenticate(transactionData).collect { event ->
                    handleAuthenticationEvent(event)
                }
            } catch (e: Exception) {
                showToastAndNavigateUp("Error: ${e.message}")
            }
        }
    }

    private fun getRetriesRemainingMessage(retries: Int): String {
        return when {
            retries == 1 -> "1 retry remaining"
            retries > 1 -> "$retries retries remaining"
            else -> "Please try again later"
        }
    }

    private fun handleRegistrationEvent(event: CaptureController.RegistrationEvent) {
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

    private fun handleAuthenticationEvent(event: CaptureController.AuthenticationEvent) {
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

    private fun navigateUp() {
        emitEvent(OOTPUiEvent.NavigateUp)
    }

    private fun enableRetryAndShowToast(message: String?) {
        emitEvents(OOTPUiEvent.EnableRetry, OOTPUiEvent.ShowToast(message ?: ""))
    }

    private fun showToastAndNavigateUp(message: String?) {
        emitEvents(OOTPUiEvent.ShowToast(message ?: ""), OOTPUiEvent.NavigateUp)
    }

    private fun emitEvents(vararg events: OOTPUiEvent) {
        events.forEach { emitEvent(it) }
    }
}
