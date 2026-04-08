package com.daon.fido.sdk.sample.kt.screens.home

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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

/**
 * HomeScreen is the landing screen after user login. User can initiate step-up authentication,
 * navigate to registration screen and perform a reset from this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: String,
    sessionId: String,
    onNavigateToRegistration: (sessionId: String) -> Unit,
    backToIntro: () -> Unit,
    onNavigateToChooseAuth: (() -> Unit, ViewModel) -> Unit,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFace: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToOOTP: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToTransactionConfirmation: (() -> Unit, ViewModel) -> Unit,
) {
    val viewModel = hiltViewModel<HomeViewModel>()
    val uiState by viewModel.state.collectAsState()
    val eventFlow = viewModel.eventFlow
    val context = LocalContext.current
    val silentAuthState by viewModel.silentAuthState.collectAsState()
    val isSilentInProgress = silentAuthState.inProgress

    LaunchedEffect(sessionId) { viewModel.setSessionId(sessionId) }

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is HomeUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is HomeUiEvent.NavigateBackToIntro -> {
                    backToIntro()
                }
                is HomeUiEvent.NavigateToChooseAuthenticator -> {
                    onNavigateToChooseAuth(onNavigateUp, viewModel)
                }

                else -> {}
            }
        }
    }

    LaunchedEffect(uiState) {
        handleUiState(
            uiState,
            viewModel,
            context,
            onNavigateToPasscode,
            onNavigateToFingerprint,
            onNavigateToFace,
            onNavigateToVoice,
            onNavigateToOOTP,
            onNavigateToTransactionConfirmation,
            onNavigateUp,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Welcome",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = user,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 32.dp),
        )

        CircularIndeterminateProgressBar(isDisplayed = uiState.inProgress)

        PrimaryFloatingButton(
            text = stringResource(R.string.action_step_up_auth),
            onClick = { viewModel.authenticate(false) },
            modifier = Modifier.padding(bottom = 16.dp),
            enabled = !uiState.inProgress,
        )

        val title =
            if (isSilentInProgress) R.string.action_stop_silent_background
            else R.string.action_start_silent_background
        PrimaryFloatingButton(
            text = stringResource(title),
            onClick = {
                if (isSilentInProgress) viewModel.stopSilentAuthInBackground()
                else viewModel.startSilentAuthInBackground()
            },
            modifier = Modifier.padding(bottom = 16.dp),
            enabled = !uiState.inProgress,
        )

        PrimaryFloatingButton(
            text = stringResource(R.string.action_single_shot),
            onClick = { viewModel.authenticate(true) },
            modifier = Modifier.padding(bottom = 16.dp),
            enabled = !uiState.inProgress,
        )

        PrimaryFloatingButton(
            text = stringResource(R.string.action_manage_authenticators),
            onClick = { onNavigateToRegistration(sessionId) },
            modifier = Modifier.padding(bottom = 16.dp),
            enabled = !uiState.inProgress,
        )

        PrimaryFloatingButton(
            text = stringResource(R.string.action_delete),
            onClick = { viewModel.delete() },
            enabled = !uiState.inProgress,
        )
    }

    BackHandler(
        enabled = true,
        onBack = {
            viewModel.doLogout()
            backToIntro()
        },
    )
}

private fun handleUiState(
    uiState: HomeScreenState,
    viewModel: HomeViewModel,
    context: Context,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToFace: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToOOTP: () -> Unit,
    onNavigateToTransactionConfirmation: (() -> Unit, ViewModel) -> Unit,
    onNavigateUp: () -> Unit,
) {
    if (uiState.authenticationState.groupSelected) {
        viewModel.clearSelectedGroup()
        when (uiState.authenticationState.group?.getAuthenticator()?.aaid) {
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

    if (uiState.transactionState.transactionConfirmationRequired) {
        onNavigateToTransactionConfirmation(onNavigateUp, viewModel)
    }

    uiState.transactionState.transactionConfirmationResult?.let { result ->
        viewModel.submitDisplayTransactionResult(result)
    }
}
