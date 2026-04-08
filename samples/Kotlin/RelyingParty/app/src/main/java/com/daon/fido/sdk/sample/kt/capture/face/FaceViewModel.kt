package com.daon.fido.sdk.sample.kt.capture.face

import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.daon.sdk.authenticator.controller.CaptureController
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.face.Config
import com.daon.sdk.face.LivenessResult
import com.daon.sdk.face.Result
import com.daon.sdk.faceauthenticator.controller.FaceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Data class representing the UI state for the face capture process. */
data class FaceUIState(
    val retakePhotoEnabled: Boolean = false,
    val doneButtonEnabled: Boolean = false,
    val infoTextVisible: Boolean = false,
    val infoText: String = "",
    val previewImageVisible: Boolean = false,
    val previewImage: Bitmap? = null,
    val isEnrollment: Boolean = true,
    val inProgress: Boolean = false,
)

sealed class FaceUiEvent {
    data class ShowToast(val message: String) : FaceUiEvent()

    data object NavigateUp : FaceUiEvent()

    data object EnableRetry : FaceUiEvent()
}

/**
 * FaceViewModel manages the face capture process, registration and authentication.
 *
 * @param application The application context.
 * @param prefs The SharedPreferences instance.
 */
@HiltViewModel
class FaceViewModel
@Inject
constructor(application: Application, private val prefs: SharedPreferences) :
    AndroidViewModel(application) {

    private val tag = FaceViewModel::class.java.simpleName
    private val _faceUIState = MutableStateFlow(FaceUIState())
    val faceUIState = _faceUIState

    private val _eventFlow = MutableSharedFlow<FaceUiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    lateinit var faceController: FaceController
    private lateinit var lifecycleOwner: LifecycleOwner

    // Cache for message strings to avoid allocations
    private var cachedQualityMessage = ""
    private var cachedLivenessMessage = ""

    private suspend fun emitEvent(event: FaceUiEvent) {
        _eventFlow.emit(event)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up bitmap to prevent memory leaks
        recycleBitmap()
    }

    /** Recycle the current preview bitmap to free memory. */
    private fun recycleBitmap() {
        _faceUIState.value.previewImage?.let { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    /**
     * Starts the face capture process.
     *
     * @param lifecycleOwner The LifecycleOwner associated with the capture process.
     * @param previewView The PreviewView to display the camera preview.
     */
    fun startCapture(
        controller: FaceController,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView? = null,
    ) {
        faceController = controller
        this.lifecycleOwner = lifecycleOwner

        val parameters = Bundle()
        // A higher threshold percentage means a less strict centering requirement.
        parameters.putFloat(Config.QUALITY_THRESHOLD_CENTERED, 10.0f)

        faceController.startCamera(
            getApplication<Application>().baseContext,
            lifecycleOwner,
            previewView,
            parameters,
        )

        viewModelScope.launch {
            faceController.startFaceCapture().collect { event -> handleFaceCaptureEvent(event) }
        }
        getAuthenticationMode()
    }

    private fun handleFaceCaptureEvent(event: FaceController.FaceCaptureEvent) {
        when (event) {
            is FaceController.FaceCaptureEvent.FaceDetected -> {
                handleFaceDetection(event.result)
            }
            is FaceController.FaceCaptureEvent.PhotoCaptured -> {
                handlePhotoCaptured(event.bitmap)
            }
        }
    }

    private fun handleFaceDetection(result: Result) {
        var lastMessageTime = 0L
        val messageDelayMs = 1000L

        val currentTime = System.currentTimeMillis()

        // Only update if enough time has passed to reduce allocations
        if (currentTime - lastMessageTime >= messageDelayMs) {
            // Cache messages to avoid repeated allocations
            cachedQualityMessage = getQualityMessage(result)

            if (cachedLivenessMessage.isEmpty()) {
                cachedLivenessMessage = getLivenessMessage(result)
            }

            val msg =
                if (cachedLivenessMessage.isNotEmpty()) {
                    "$cachedQualityMessage\n\n$cachedLivenessMessage"
                } else {
                    cachedQualityMessage
                }

            Logger.logDebug(tag, "FaceDetectionHandler faceDetection: $msg")
            _faceUIState.update { it.copy(infoTextVisible = true, infoText = msg) }

            lastMessageTime = currentTime
        }
    }

    private fun handlePhotoCaptured(bitmap: Bitmap) {
        Logger.logDebug(tag, "FaceDetectionHandler photoCaptured")
        // Recycle old bitmap before setting new one
        recycleBitmap()

        _faceUIState.update { currentUIState ->
            currentUIState.copy(
                previewImageVisible = true,
                previewImage = bitmap,
                retakePhotoEnabled = true,
                doneButtonEnabled = true,
                infoText = "",
                infoTextVisible = false,
            )
        }
        // Auto capture for authentication
        if (faceUIState.value.isEnrollment) {
            _faceUIState.update { currentUIState -> currentUIState.copy(doneButtonEnabled = true) }
        } else {
            authenticate()
        }
    }

    /** Stops the face capture process. */
    fun stopCapture() {
        if (this::faceController.isInitialized) faceController.stopFaceCapture()
    }

    /**
     * Returns the message indicating the number of retries remaining.
     *
     * @return The message indicating the number of retries remaining.
     */
    private fun getRetriesRemainingMessage(retries: Int): String {
        return when {
            retries == 1 -> "1 retry remaining"
            retries > 1 -> "$retries retries remaining"
            else -> "Please try again later"
        }
    }

    /**
     * Returns the message indicating the quality of the captured image.
     *
     * @param result The Result of the capture.
     * @return The message indicating the quality of the captured image.
     */
    private fun getQualityMessage(result: Result): String {
        return when {
            !result.isDeviceUpright -> "Hold device upright"
            result.qualityResult.hasMask() -> "Remove medical mask"
            !result.qualityResult.hasAcceptableEyeDistance() -> "Move device closer"
            !result.qualityResult.hasAcceptableQuality() -> {
                val goodLighting =
                    result.qualityResult.hasAcceptableExposure() &&
                        result.qualityResult.hasUniformLighting() &&
                        result.qualityResult.hasAcceptableGrayscaleDensity()

                if (!goodLighting) {
                    "Improve lighting conditions"
                } else {
                    "Low quality image"
                }
            }
            !result.qualityResult.isFaceCentered -> "Keep face centered"
            result.livenessResult.alert == LivenessResult.ALERT_FACE_TOO_FAR -> "Move device closer"
            result.livenessResult.alert == LivenessResult.ALERT_FACE_TOO_NEAR ->
                "Move device further away"
            else -> "Look alive!"
        }
    }

    // Returns the message indicating the liveness of the captured image.
    private fun getLivenessMessage(result: Result): String {
        return when {
            result.livenessResult.isBlink -> "Blink detected"
            result.livenessResult.isPassive -> "Passive liveness detected"
            result.livenessResult.spoofDetected() -> "Spoof detected"
            else -> ""
        }
    }

    fun onRecapture(
        faceController: FaceController,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) {
        stopCapture()
        resetFaceUIState()
        startCapture(faceController, lifecycleOwner, previewView)
    }

    // resets the UI state to its initial values
    fun resetFaceUIState() {
        // Recycle bitmap before clearing state
        recycleBitmap()

        _faceUIState.update {
            it.copy(
                retakePhotoEnabled = false,
                doneButtonEnabled = false,
                infoTextVisible = false,
                infoText = "",
                previewImageVisible = false,
                previewImage = null,
                isEnrollment = false,
                inProgress = false,
            )
        }
    }

    /** Gets the authentication mode. */
    private fun getAuthenticationMode() {
        _faceUIState.update { it.copy(isEnrollment = faceController.isEnrol) }
    }

    /** Registers the captured image. */
    fun register() {
        _faceUIState.update {
            it.copy(inProgress = true, retakePhotoEnabled = false, doneButtonEnabled = false)
        }
        viewModelScope.launch {
            // Note: Don't recycle bitmap here as it's still being used by the controller
            faceController.register().collect { event ->
                when (event) {
                    is CaptureController.RegistrationEvent.Success -> {
                        _faceUIState.update { it.copy(inProgress = false) }
                        resetFaceUIState()
                        showToastAndNavigateUp("Registration Successful !!")
                    }
                    is CaptureController.RegistrationEvent.Failure.Validation -> {
                        _faceUIState.update { it.copy(inProgress = false) }
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
                        _faceUIState.update { it.copy(inProgress = false) }
                        resetFaceUIState()
                        showToastAndNavigateUp("Registration failed: ${event.error?.message}")
                    }
                }
            }
        }
    }

    /** Authenticates the captured image. */
    fun authenticate() {
        _faceUIState.update {
            it.copy(inProgress = true, retakePhotoEnabled = false, doneButtonEnabled = false)
        }
        // Note: Don't recycle bitmap here as it's still being used by the controller
        viewModelScope.launch {
            faceController.authenticate().collect { event ->
                when (event) {
                    is CaptureController.AuthenticationEvent.Success -> {
                        _faceUIState.update { it.copy(inProgress = false) }
                        resetFaceUIState()
                        showToastAndNavigateUp("Authentication Successful !!")
                    }
                    is CaptureController.AuthenticationEvent.Failure.Validation -> {
                        _faceUIState.update { it.copy(inProgress = false) }
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
                        _faceUIState.update { it.copy(inProgress = false) }
                        resetFaceUIState()
                        if (event.error != null) {
                            showToastAndNavigateUp("Authentication failed: ${event.error?.message}")
                        } else {
                            navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun emitEvents(vararg events: FaceUiEvent) {
        viewModelScope.launch { events.forEach { emitEvent(it) } }
    }

    private fun navigateUp() {
        emitEvents(FaceUiEvent.NavigateUp)
    }

    private fun showToastAndNavigateUp(message: String?) {
        emitEvents(FaceUiEvent.ShowToast(message ?: ""), FaceUiEvent.NavigateUp)
    }

    private fun enableRetryAndShowToast(message: String?) {
        emitEvents(FaceUiEvent.EnableRetry, FaceUiEvent.ShowToast(message ?: ""))
    }
}
