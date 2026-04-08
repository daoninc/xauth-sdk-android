package com.daon.fido.sdk.sample.kt.screens.push

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.daon.fido.sdk.sample.kt.R
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

/**
 * PushAuthenticationScreen handles push notification triggered authentication. Displays progress
 * while authentication is in progress and handles authenticator selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushAuthenticationScreen(
    transactionId: String,
    onNavigateToChooseAuth: (() -> Unit, ViewModel) -> Unit,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFace: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToOOTP: () -> Unit,
    onNavigateToTransactionConfirmation: () -> Unit,
    onNavigateUp: () -> Unit,
    onAuthenticationComplete: (message: String?) -> Unit,
) {
    val viewModel = hiltViewModel<PushAuthenticationViewModel>()
    val uiState by viewModel.state.collectAsState()
    val eventFlow = viewModel.eventFlow
    val context = LocalContext.current

    // Handle successful authentication completion - pop entire push flow
    LaunchedEffect(uiState.authenticationCompleted) {
        if (uiState.authenticationCompleted) {
            onAuthenticationComplete(uiState.completionMessage)
        }
    }

    // Start push authentication only once when the screen is first composed
    LaunchedEffect(Unit) {
        if (transactionId.isNotEmpty() && !uiState.authenticationStarted) {
            viewModel.startPushAuthentication(transactionId)
        }
    }

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            // Skip navigation events if authentication is already completed
            val currentState = viewModel.state.value
            if (currentState.authenticationCompleted) return@collect

            when (event) {
                is PushAuthenticationUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is PushAuthenticationUiEvent.NavigateBack -> {
                    onNavigateUp()
                }
                is PushAuthenticationUiEvent.NavigateToChooseAuthenticator -> {
                    onNavigateToChooseAuth(onNavigateUp, viewModel)
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(uiState) {
        // Don't process navigation if authentication is already completed
        if (!uiState.authenticationCompleted) {
            handleUiState(
                uiState,
                viewModel,
                context,
                onNavigateToPasscode,
                onNavigateToFingerprint,
                onNavigateToFace,
                onNavigateToVoice,
                onNavigateToOOTP,
            )
        }
    }

    // Handle transaction confirmation navigation
    LaunchedEffect(uiState.transactionState.transactionConfirmationRequired) {
        if (uiState.transactionState.transactionConfirmationRequired) {
            onNavigateToTransactionConfirmation()
        }
    }

    // Handle transaction confirmation result submission
    LaunchedEffect(uiState.transactionState.transactionConfirmationResult) {
        uiState.transactionState.transactionConfirmationResult?.let { result ->
            viewModel.submitDisplayTransactionResult(result)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.push_authentication_title),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.push_authentication_in_progress),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            CircularIndeterminateProgressBar(isDisplayed = uiState.inProgress)

            if (!uiState.inProgress && !uiState.authenticationCompleted) {
                Text(
                    text = stringResource(R.string.push_authentication_waiting),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    BackHandler(enabled = true, onBack = { onNavigateUp() })
}

private fun handleUiState(
    uiState: PushAuthenticationState,
    viewModel: PushAuthenticationViewModel,
    context: Context,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToFace: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToOOTP: () -> Unit,
) {
    if (uiState.authenticationState.groupSelected) {
        val aaid = uiState.authenticationState.group?.getAuthenticator()?.aaid
        viewModel.clearSelectedGroup()
        when (aaid) {
            SRP_PASSCODE,
            PASSCODE -> {
                val passcodeController = viewModel.getPasscodeController()
                if (passcodeController != null) {
                    onNavigateToPasscode()
                } else {
                    Toast.makeText(
                            context,
                            "Passcode controller is not available",
                            Toast.LENGTH_SHORT,
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
                            "Fingerprint controller is not available",
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
            }

            FACE,
            ADOS_FACE -> {
                val faceController = viewModel.getFaceController()
                if (faceController != null) {
                    onNavigateToFace()
                } else {
                    Toast.makeText(context, "Face controller is not available", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            VOICE,
            ADOS_VOICE -> {
                val voiceController = viewModel.getVoiceController()
                if (voiceController != null) {
                    onNavigateToVoice()
                } else {
                    Toast.makeText(context, "Voice controller is not available", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            OOTP -> {
                val ootpController = viewModel.getOotpController()
                if (ootpController != null) {
                    onNavigateToOOTP()
                } else {
                    Toast.makeText(context, "OOTP controller is not available", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            SILENT -> {
                viewModel.authenticateSilent()
            }
        }
    }
}
