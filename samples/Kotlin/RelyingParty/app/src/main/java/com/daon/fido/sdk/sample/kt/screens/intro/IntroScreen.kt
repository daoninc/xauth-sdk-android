package com.daon.fido.sdk.sample.kt.screens.intro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daon.fido.sdk.sample.kt.ui.theme.PrimaryFloatingButton
import com.daon.fido.sdk.sample.kt.util.CircularIndeterminateProgressBar
import com.daon.sdk.xauth.util.AuthenticatorIds.ADOS_FACE
import com.daon.sdk.xauth.util.AuthenticatorIds.ADOS_VOICE
import com.daon.sdk.xauth.util.AuthenticatorIds.FACE
import com.daon.sdk.xauth.util.AuthenticatorIds.FINGERPRINT
import com.daon.sdk.xauth.util.AuthenticatorIds.OOTP
import com.daon.sdk.xauth.util.AuthenticatorIds.PASSCODE
import com.daon.sdk.xauth.util.AuthenticatorIds.SILENT
import com.daon.sdk.xauth.util.AuthenticatorIds.SRP_PASSCODE
import com.daon.sdk.xauth.util.AuthenticatorIds.VOICE

// Core permissions required for app functionality
private val CORE_PERMISSIONS =
    listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
    )

/*
 * This file defines the `IntroScreen` composable, which is the initial screen of the application.
 * It handles user interactions for logging in and creating an account, and manages
 * navigation to other screens.
 */
@Composable
fun IntroScreen(
    onNavigateToHome: (username: String, sessionId: String) -> Unit,
    onNavigateToChooseAuth: (() -> Unit, ViewModel) -> Unit,
    onNavigateToAccounts: (() -> Unit, ViewModel) -> Unit,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFace: (ViewModel) -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToOOTP: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val viewModel = hiltViewModel<IntroViewModel>()
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val eventFlow = viewModel.eventFlow
    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissions ->
            // Core permissions must be granted for app to function
            val corePermissionsGranted = CORE_PERMISSIONS.all { permissions[it] == true }

            if (!corePermissionsGranted) {
                Toast.makeText(
                        context,
                        "Core permissions are required for the app to function",
                        Toast.LENGTH_LONG,
                    )
                    .show()
                onNavigateUp()
            } else {
                // Notification permission is optional - inform user if denied
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        permissions[Manifest.permission.POST_NOTIFICATIONS] != true
                ) {
                    Toast.makeText(
                            context,
                            "Push notifications are disabled. Enable in settings.",
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                viewModel.startGps()
            }
        }

    LaunchedEffect(Unit) {
        val permissions =
            buildList {
                    addAll(CORE_PERMISSIONS)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                .toTypedArray()

        val allPermissionsGranted =
            permissions.all { permission ->
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }

        if (!allPermissionsGranted) {
            permissionsLauncher.launch(permissions)
        } else {
            viewModel.startGps()
        }
    }

    // Initialize FIDO and request permissions when the app is started
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.configureLogging()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is IntroUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is IntroUiEvent.NavigateToHome -> {
                    onNavigateToHome(uiState.username.toString(), uiState.sessionId.toString())
                }
                is IntroUiEvent.NavigateToChooseAuthenticator -> {
                    onNavigateToChooseAuth(onNavigateUp, viewModel)
                }

                else -> {}
            }
        }
    }

    // Observe the UI state
    LaunchedEffect(uiState) {
        handleUiState(
            uiState,
            context,
            viewModel,
            onNavigateUp,
            onNavigateToAccounts,
            onNavigateToPasscode,
            onNavigateToFingerprint,
            onNavigateToFace,
            onNavigateToVoice,
            onNavigateToOOTP,
        )
    }

    // Layout for the IntroScreen
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "IdentityX FIDO Sample",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 40.sp,
            )
            Spacer(modifier = Modifier.height(32.dp))
            PrimaryFloatingButton(
                text = "Log in with Fido",
                onClick = {
                    viewModel.clearUiState()
                    viewModel.authenticate()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.inProgress,
            )
            CircularIndeterminateProgressBar(isDisplayed = uiState.inProgress)
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryFloatingButton(
                text = "Create Account",
                onClick = { viewModel.createAccount() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.inProgress,
            )
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryFloatingButton(
                text = "Generate OOTP",
                onClick = { viewModel.generateOOTP() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.inProgress,
            )
        }

        // Hint for developer settings (double-tap gesture)
        Text(
            text = "Double tap to open developer settings",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }

    // OOTP Result Overlay
    // Uses an inline Compose overlay instead of AlertDialog to avoid Samsung One UI
    // rendering issues where the platform Dialog scrim (FLAG_DIM_BEHIND) fails to
    // clean up after dismissal, leaving the background faded/shaded (FSDK-1392).
    if (uiState.ootpGenerationState.showResult) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Generated OOTP",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "OTP: ${uiState.ootpGenerationState.ootpValue ?: "N/A"}",
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (!uiState.ootpGenerationState.transactionData.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text =
                                "Transaction Data: ${uiState.ootpGenerationState.transactionData}",
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { viewModel.dismissOOTPResult() },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("OK", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun handleUiState(
    uiState: IntroScreenState,
    context: Context,
    viewModel: IntroViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToAccounts: (() -> Unit, ViewModel) -> Unit,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToFace: (ViewModel) -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToOOTP: () -> Unit,
) {
    // Handle account selection for non-ADoS authentication
    if (uiState.accountSelectionState.accountListAvailable) {
        onNavigateToAccounts(onNavigateUp, viewModel)
    }

    if (uiState.accountSelectionState.accountSelected) {
        viewModel.submitSelectedAccount()
    }

    // Handle authentication selection
    if (uiState.loginProcessState.groupSelected) {
        viewModel.clearSelectedGroup()
        viewModel.setInProgressFalse()
        when (uiState.loginProcessState.group?.getAuthenticator()?.aaid) {
            SRP_PASSCODE,
            PASSCODE -> {
                val passcodeController = viewModel.getPasscodeController()
                if (passcodeController != null) {
                    onNavigateToPasscode()
                } else {
                    Toast.makeText(
                            context,
                            "Passcode controller is not available.",
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
            }

            FINGERPRINT -> {
                val fingerprintController = viewModel.getFingerprintController()
                if (fingerprintController != null) {
                    onNavigateToFingerprint()
                } else {
                    Toast.makeText(
                            context,
                            "Fingerprint controller is not available.",
                            Toast.LENGTH_LONG,
                        )
                        .show()
                }
            }

            FACE,
            ADOS_FACE -> {
                val faceController = viewModel.getFaceController()
                if (faceController != null) {
                    onNavigateToFace(viewModel)
                } else {
                    Toast.makeText(context, "Face controller is not available.", Toast.LENGTH_LONG)
                        .show()
                }
            }

            VOICE,
            ADOS_VOICE -> {
                val voiceController = viewModel.getVoiceController()
                if (voiceController != null) {
                    onNavigateToVoice()
                } else {
                    Toast.makeText(context, "Voice controller is not available.", Toast.LENGTH_LONG)
                        .show()
                }
            }

            OOTP -> {
                val ootpController = viewModel.getOotpController()
                if (ootpController != null) {
                    onNavigateToOOTP()
                } else {
                    Toast.makeText(context, "OOTP controller is not available.", Toast.LENGTH_LONG)
                        .show()
                }
            }

            SILENT -> {
                viewModel.authenticateSilent()
            }
        }
    }
}
