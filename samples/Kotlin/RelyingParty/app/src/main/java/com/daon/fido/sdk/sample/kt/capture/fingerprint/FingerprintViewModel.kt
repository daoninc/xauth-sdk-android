package com.daon.fido.sdk.sample.kt.capture.fingerprint

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.sdk.authenticator.controller.BiometricController
import com.daon.sdk.authenticator.controller.BiometricController.BiometricCaptureEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class FingerprintUiEvent {
    data class ShowToast(val message: String) : FingerprintUiEvent()

    data object NavigateUp : FingerprintUiEvent()
}

/**
 * FingerprintViewModel handles the state and logic for the fingerprint authentication.
 *
 * @param application The application context.
 */
@HiltViewModel
class FingerprintViewModel @Inject constructor(application: Application) :
    AndroidViewModel(application) {

    // Fingerprint capture controller
    private lateinit var fingerController: BiometricController

    private val _eventFlow = MutableSharedFlow<FingerprintUiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private suspend fun emitEvent(event: FingerprintUiEvent) {
        _eventFlow.emit(event)
    }

    /**
     * Starts the fingerprint capture process.
     *
     * @param context The context to use for starting the capture.
     */
    fun onStart(context: Context, controller: BiometricController) {
        fingerController = controller
        val activity = context.findActivity() as FragmentActivity

        // Start the fingerprint capture
        viewModelScope.launch {
            fingerController.startCapture(activity).collect { event -> handleCaptureEvent(event) }
        }
    }

    // For Fingerprint - CaptureEvents are only Success and Failure (Validation, Fatal).
    private suspend fun handleCaptureEvent(event: BiometricCaptureEvent) {
        when (event) {
            is BiometricCaptureEvent.Success -> {
                showToastAndNavigateUp("Fingerprint capture complete")
            }
            is BiometricCaptureEvent.Failure.Validation -> {
                showToastAndNavigateUp("Fingerprint capture failure: ${event.error.message}")
            }
            is BiometricCaptureEvent.Failure.Fatal -> {
                showToastAndNavigateUp("Fingerprint capture failure: ${event.error.message}")
            }
        }
    }

    /** Stops the fingerprint capture process. */
    fun onStop() {
        if (::fingerController.isInitialized) fingerController.stopCapture()
    }

    /**
     * Finds the activity from the context.
     *
     * @return The activity if found, otherwise null.
     */
    private fun Context.findActivity(): AppCompatActivity? =
        when (this) {
            is AppCompatActivity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }

    private suspend fun emitEvents(vararg events: FingerprintUiEvent) {
        events.forEach { emitEvent(it) }
    }

    private suspend fun showToastAndNavigateUp(message: String?) {
        emitEvents(FingerprintUiEvent.ShowToast(message ?: ""), FingerprintUiEvent.NavigateUp)
    }
}
