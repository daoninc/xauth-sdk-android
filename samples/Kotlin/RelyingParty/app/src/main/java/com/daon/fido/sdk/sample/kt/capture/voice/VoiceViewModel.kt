package com.daon.fido.sdk.sample.kt.capture.voice

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daon.sdk.authenticator.controller.CaptureController
import com.daon.sdk.voice.DaonVoice
import com.daon.sdk.voiceauthenticator.controller.VoiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Data class representing the UI state for the voice capture process. */
data class VoiceUIState(
    val voiceInitialized: Boolean = false,
    val recording: Boolean = false,
    val inProgress: Boolean = false,
    val phraseCount: String = "1 of 3",
    val voicePhrase: String = " ",
)

sealed class VoiceUiEvent {
    data class ShowToast(val message: String) : VoiceUiEvent()

    data object NavigateUp : VoiceUiEvent()

    data object EnableRetry : VoiceUiEvent()
}

/**
 * VoiceViewModel manages the voice capture process, registration and authentication.
 *
 * @param application The application context.
 * @param prefs The SharedPreferences instance.
 */
@HiltViewModel
class VoiceViewModel
@Inject
constructor(application: Application, private val prefs: SharedPreferences) :
    AndroidViewModel(application) {

    private val DEFAULT_RECORD_TIMEOUT = 10000
    private val NUMBER_OF_PHRASES = 3

    // UI state
    private val _voiceUIState = MutableStateFlow(VoiceUIState())
    val voiceUIState = _voiceUIState

    private val _eventFlow = MutableSharedFlow<VoiceUiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private lateinit var voiceController: VoiceController

    private suspend fun emitEvent(event: VoiceUiEvent) {
        _eventFlow.emit(event)
    }

    fun startCapture(voiceController: VoiceController) {
        this.voiceController = voiceController

        viewModelScope.launch {
            voiceController.startVoiceCapture().collect { event -> handleVoiceInitEvent(event) }
        }
    }

    private suspend fun handleVoiceInitEvent(event: VoiceController.VoiceInitEvent) {
        when (event) {
            is VoiceController.VoiceInitEvent.Initialized -> {
                _voiceUIState.update {
                    it.copy(voiceInitialized = true, voicePhrase = voiceController.phraseToSpeak)
                }
            }
            is VoiceController.VoiceInitEvent.Failure -> {
                showToastAndNavigateUp("Voice initialization failed: ${event.exception?.message}")
            }
        }
    }

    private suspend fun handleRecordingEvent(event: VoiceController.RecordingEvent) {
        when (event) {
            is VoiceController.RecordingEvent.Timeout -> {
                showToast("Recording timeout")
                voiceController.stopRecording()
            }
        }
    }

    private suspend fun handleAudioProcessingEvent(event: VoiceController.AudioProcessingEvent) {
        when (event) {
            is VoiceController.AudioProcessingEvent.Complete -> {
                if (event.errorCode != DaonVoice.RESULT_PASS) {
                    showToast(voiceController.getVoiceSdkErrorMessage(event.errorCode))
                } else {
                    // Sample is now automatically stored in the SDK
                    val collectedCount = voiceController.getCollectedSamplesCount()

                    if (voiceController.configuration.isEnrol()) {
                        // Enrollment - check if we have enough samples
                        val done = collectedCount >= NUMBER_OF_PHRASES
                        if (done) {
                            _voiceUIState.update { it.copy(inProgress = true) }
                            voiceController.register().collect { registrationEvent ->
                                handleRegistrationEvent(registrationEvent)
                            }
                        } else {
                            // Update the steps in the UI - ready to start again
                            _voiceUIState.update {
                                it.copy(phraseCount = "${collectedCount + 1} of $NUMBER_OF_PHRASES")
                            }
                        }
                    } else {
                        // Authentication - only need one sample
                        _voiceUIState.update { it.copy(inProgress = true) }
                        voiceController.authenticate().collect { authEvent ->
                            handleAuthenticationEvent(authEvent)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleRegistrationEvent(event: CaptureController.RegistrationEvent) {
        when (event) {
            is CaptureController.RegistrationEvent.Success -> {
                _voiceUIState.update { it.copy(inProgress = false) }
                showToastAndNavigateUp("Voice enrolled successfully")
            }
            is CaptureController.RegistrationEvent.Failure.Validation -> {
                _voiceUIState.update { it.copy(inProgress = false) }
                val message =
                    if (event.error.message != null) {
                        event.error.message +
                            "\n" +
                            getRetriesRemainingMessage(event.retriesRemaining)
                    } else {
                        getRetriesRemainingMessage(event.retriesRemaining)
                    }
                enableRetryAndShowToast(message)
            }
            is CaptureController.RegistrationEvent.Failure.Fatal -> {
                _voiceUIState.update { it.copy(inProgress = false) }
                showToastAndNavigateUp("Voice enrollment failed: ${event.error?.message}")
            }
        }
    }

    private suspend fun handleAuthenticationEvent(event: CaptureController.AuthenticationEvent) {
        when (event) {
            is CaptureController.AuthenticationEvent.Success -> {
                _voiceUIState.update { it.copy(inProgress = false) }
                showToastAndNavigateUp("Voice verified successfully")
            }
            is CaptureController.AuthenticationEvent.Failure.Validation -> {
                _voiceUIState.update { it.copy(inProgress = false) }
                val message =
                    if (event.error.message != null) {
                        event.error.message +
                            "\n" +
                            getRetriesRemainingMessage(event.retriesRemaining)
                    } else {
                        getRetriesRemainingMessage(event.retriesRemaining)
                    }
                enableRetryAndShowToast(message)
            }
            is CaptureController.AuthenticationEvent.Failure.Fatal -> {
                _voiceUIState.update { it.copy(inProgress = false) }
                if (event.error != null) {
                    showToastAndNavigateUp("Voice verification failed: ${event.error?.message}")
                } else {
                    navigateUp()
                }
            }
        }
    }

    fun isRecording(): Boolean {
        return voiceController.isRecording()
    }

    fun startRecording() {
        viewModelScope.launch {
            voiceController.startRecording(DEFAULT_RECORD_TIMEOUT).collect {
                handleRecordingEvent(it)
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            voiceController.stopRecording().collect { handleAudioProcessingEvent(it) }
        }
    }

    fun cancelOnBackground() {
        if (!::voiceController.isInitialized) return
        voiceController.cancelRecording()
        voiceController.clearCollectedSamples()
        resetVoiceUIState()
    }

    fun resetVoiceUIState() {
        _voiceUIState.update {
            it.copy(recording = false, inProgress = false, phraseCount = "1 of $NUMBER_OF_PHRASES")
        }
    }

    fun toggleRecording() {
        if (isRecording()) {
            stopRecording()
        } else {
            startRecording()
        }
        _voiceUIState.update { it.copy(recording = !it.recording) }
    }

    /**
     * Returns the message indicating the number of retries remaining.
     *
     * @param retries The number of retries remaining.
     * @return The message indicating the number of retries remaining.
     */
    private fun getRetriesRemainingMessage(retries: Int): String {
        return when {
            retries == 1 -> "1 retry remaining"
            retries > 1 -> "$retries retries remaining"
            else -> "Please try again later"
        }
    }

    private suspend fun emitEvents(vararg events: VoiceUiEvent) {
        events.forEach { emitEvent(it) }
    }

    private suspend fun navigateUp() {
        emitEvents(VoiceUiEvent.NavigateUp)
    }

    private suspend fun showToast(message: String?) {
        emitEvent(VoiceUiEvent.ShowToast(message ?: ""))
    }

    private suspend fun showToastAndNavigateUp(message: String?) {
        emitEvents(VoiceUiEvent.ShowToast(message ?: ""), VoiceUiEvent.NavigateUp)
    }

    private suspend fun enableRetryAndShowToast(message: String?) {
        emitEvents(VoiceUiEvent.EnableRetry, VoiceUiEvent.ShowToast(message ?: ""))
    }
}
