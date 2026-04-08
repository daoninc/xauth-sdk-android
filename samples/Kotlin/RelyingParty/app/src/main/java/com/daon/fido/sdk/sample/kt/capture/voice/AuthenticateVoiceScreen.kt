package com.daon.fido.sdk.sample.kt.capture.voice

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daon.fido.sdk.sample.kt.R
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.sdk.voiceauthenticator.controller.VoiceController
import kotlinx.coroutines.flow.collectLatest

/**
 * UI for the voice capture process.
 *
 * @param onNavigateUp: Callback function to handle navigation when voice capture is complete.
 * @param voiceController: The voice controller to use for the voice capture process.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticateVoiceScreen(onNavigateUp: () -> Unit, voiceController: VoiceController) {
    val viewModel = hiltViewModel<VoiceViewModel>()
    val uiState by viewModel.voiceUIState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val eventFlow = viewModel.eventFlow
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted
            ->
            hasPermission = isGranted
            if (!isGranted) {
                Toast.makeText(
                        context,
                        "Record audio permission is required for voice authentication",
                        Toast.LENGTH_LONG,
                    )
                    .show()
                onNavigateUp()
            }
        }

    LaunchedEffect(Unit) {
        val permission = Manifest.permission.RECORD_AUDIO
        hasPermission = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.startCapture(voiceController)
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.cancelOnBackground()
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
                is VoiceUiEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is VoiceUiEvent.NavigateUp -> onNavigateUp()
                is VoiceUiEvent.EnableRetry -> viewModel.resetVoiceUIState()
            }
        }
    }

    BackHandler { onNavigateUp() }

    Scaffold(topBar = { AppTopAppBar(title = "Voice Authentication") }) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .consumeWindowInsets(paddingValues)
                        .safeContentPadding()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.voice_info),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                Text(
                    text =
                        if (uiState.voiceInitialized) uiState.voicePhrase
                        else stringResource(id = R.string.voice_phrase),
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                )

                if (uiState.voiceInitialized) {
                    Spacer(modifier = Modifier.height(32.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    ) {
                        VoiceVisualizer(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            isAnimating = uiState.recording,
                        )
                        PlayPauseButton(
                            modifier = Modifier.size(72.dp),
                            isPlaying = uiState.recording,
                            viewModel = viewModel,
                            enabled = !uiState.inProgress,
                        )
                    }
                }
            }

            if (uiState.inProgress) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}
