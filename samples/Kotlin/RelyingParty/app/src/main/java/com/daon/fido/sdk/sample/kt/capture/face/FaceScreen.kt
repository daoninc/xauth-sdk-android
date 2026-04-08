package com.daon.fido.sdk.sample.kt.capture.face

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daon.fido.sdk.sample.kt.screens.intro.IntroViewModel
import com.daon.fido.sdk.sample.kt.ui.theme.PrimaryFloatingButton
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.faceauthenticator.controller.FaceController
import kotlinx.coroutines.flow.collectLatest

/**
 * UI for the face capture process.
 *
 * @param onNavigateUp: Callback function to handle navigation when face capture is complete.
 * @param introViewModel: The intro view model to use for navigation to accounts screen for ADoS
 *   authenticators.
 * @param onNavigateToAccounts: Callback function to navigate to accounts screen.
 * @param faceController: The face controller to use for the face capture process.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceScreen(
    onNavigateUp: () -> Unit,
    introViewModel: IntroViewModel? = null,
    onNavigateToAccounts: (() -> Unit, ViewModel) -> Unit,
    faceController: FaceController,
) {

    val viewModel = hiltViewModel<FaceViewModel>()
    val uiState by viewModel.faceUIState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val eventFlow = viewModel.eventFlow
    var isAccountChooserVisible by rememberSaveable { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted
            ->
            hasPermission = isGranted
            if (!isGranted) {
                Toast.makeText(
                        context,
                        "Camera permission is required for face authentication",
                        Toast.LENGTH_LONG,
                    )
                    .show()
                onNavigateUp()
            }
        }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.CAMERA
        hasPermission = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    introViewModel?.let {
        LaunchedEffect(key1 = it.state) {
            it.state.collect { uiState ->
                if (uiState.accountSelectionState.accountListAvailable) {
                    isAccountChooserVisible = true
                    onNavigateToAccounts(onNavigateUp, it)
                }
                if (uiState.accountSelectionState.accountSelected) {
                    it.submitSelectedAccount()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!isAccountChooserVisible && hasPermission) {
                        viewModel.startCapture(faceController, lifecycleOwner, previewView)
                    } else {
                        isAccountChooserVisible = false
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (!isAccountChooserVisible) {
                        viewModel.resetFaceUIState()
                        viewModel.stopCapture()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val activity = context as Activity
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(eventFlow) {
        eventFlow.collectLatest { event ->
            when (event) {
                is FaceUiEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is FaceUiEvent.NavigateUp -> onNavigateUp()
                is FaceUiEvent.EnableRetry ->
                    viewModel.onRecapture(faceController, lifecycleOwner, previewView)
            }
        }
    }

    BackHandler { onNavigateUp() }

    if (uiState.previewImageVisible) {
        PreviewImageLayout(
            uiState = uiState,
            viewModel = viewModel,
            faceController = faceController,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            onNavigateUp = onNavigateUp,
        )
    } else {
        CameraPreviewLayout(uiState = uiState, previewView = previewView)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewImageLayout(
    uiState: FaceUIState,
    viewModel: FaceViewModel,
    faceController: FaceController,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onNavigateUp: () -> Unit,
) {
    ConstraintLayout(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val (imageRef, infoTextRef, buttonRowRef) = createRefs()

        uiState.previewImage?.let {
            val imageBitmap = remember(it) { it.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "Preview Image",
                modifier =
                    Modifier.fillMaxSize().constrainAs(imageRef) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom)
                    },
                contentScale = ContentScale.Crop,
            )
        }

        if (uiState.infoTextVisible) {
            Text(
                text = uiState.infoText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(Color(0x4D000000))
                        .padding(8.dp)
                        .constrainAs(infoTextRef) {
                            bottom.linkTo(buttonRowRef.top, margin = 16.dp)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        },
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier.fillMaxWidth().padding(24.dp).constrainAs(buttonRowRef) {
                    bottom.linkTo(parent.bottom, margin = 32.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
        ) {
            if (uiState.retakePhotoEnabled) {
                PrimaryFloatingButton(
                    text = "Retake",
                    onClick = {
                        viewModel.onRecapture(faceController, lifecycleOwner, previewView)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (uiState.doneButtonEnabled) {
                PrimaryFloatingButton(
                    text = if (uiState.isEnrollment) "Register" else "Authenticate",
                    onClick = {
                        if (uiState.isEnrollment) {
                            viewModel.register()
                        } else {
                            viewModel.authenticate()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (uiState.inProgress) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun CameraPreviewLayout(uiState: FaceUIState, previewView: PreviewView) {
    LaunchedEffect(previewView) {
        Logger.logDebug("FaceScreen", "Setting up PreviewView")
        previewView.apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    ConstraintLayout(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val (previewLayout, infoTextView) = createRefs()

        Box(
            modifier =
                Modifier.constrainAs(previewLayout) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }
                    .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path =
                    Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                        op(
                            this,
                            Path().apply {
                                addOval(
                                    Rect(
                                        left = size.width * 0.1f,
                                        top = size.height * 0.2f,
                                        right = size.width * 0.9f,
                                        bottom = size.height * 0.8f,
                                    )
                                )
                            },
                            PathOperation.Difference,
                        )
                    }
                drawPath(path = path, color = Color.Black.copy(alpha = 0.3f))
            }
        }

        if (uiState.infoTextVisible) {
            Text(
                text = uiState.infoText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(Color(0x4D000000), shape = RoundedCornerShape(12.dp))
                        .padding(8.dp)
                        .constrainAs(infoTextView) {
                            bottom.linkTo(parent.bottom, margin = 32.dp)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        },
            )
        }

        if (uiState.inProgress) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
