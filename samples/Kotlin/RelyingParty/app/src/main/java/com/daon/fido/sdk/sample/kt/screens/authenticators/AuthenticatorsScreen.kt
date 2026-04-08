package com.daon.fido.sdk.sample.kt.screens.authenticators

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.fido.sdk.sample.kt.ui.theme.FloatingCard
import com.daon.fido.sdk.sample.kt.ui.theme.PrimaryFloatingButton
import com.daon.fido.sdk.sample.kt.util.CircularIndeterminateProgressBar
import com.daon.fido.sdk.sample.kt.util.getBitmap
import com.daon.sdk.xauth.model.Authenticator
import com.daon.sdk.xauth.util.AuthenticatorIds.ADOS_FACE
import com.daon.sdk.xauth.util.AuthenticatorIds.ADOS_VOICE
import com.daon.sdk.xauth.util.AuthenticatorIds.FACE
import com.daon.sdk.xauth.util.AuthenticatorIds.FINGERPRINT
import com.daon.sdk.xauth.util.AuthenticatorIds.OOTP
import com.daon.sdk.xauth.util.AuthenticatorIds.PASSCODE
import com.daon.sdk.xauth.util.AuthenticatorIds.SILENT
import com.daon.sdk.xauth.util.AuthenticatorIds.SRP_PASSCODE
import com.daon.sdk.xauth.util.AuthenticatorIds.VOICE

/*
 * Displays a list of registered authenticators .
 * It also handles user interactions for registering and deregistering authenticators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatorsScreen(
    sessionId: String,
    onNavigateToChooseAuth: (() -> Unit, ViewModel) -> Unit,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFace: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToOOTP: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    val viewModel = hiltViewModel<AuthenticatorsViewModel>()
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsState()
    val eventFlow = viewModel.eventFlow

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is AuthenticatorsUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is AuthenticatorsUiEvent.NavigateToChooseAuthenticator -> {
                    onNavigateToChooseAuth(onNavigateUp, viewModel)
                }

                else -> {}
            }
        }
    }

    LaunchedEffect(uiState) {
        handleUiState(
            context,
            uiState,
            viewModel,
            onNavigateToPasscode,
            onNavigateToFingerprint,
            onNavigateToFace,
            onNavigateToVoice,
            onNavigateToOOTP,
        )
    }

    DisposableEffect(key1 = viewModel) {
        viewModel.onStart()
        viewModel.setSessionId(sessionId)
        onDispose {}
    }

    Scaffold(topBar = { AppTopAppBar(title = "Registered authenticators") }) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                AuthenticatorList(uiState.discoverList, viewModel, uiState.selectedIndex)
                CircularIndeterminateProgressBar(isDisplayed = uiState.inProgress)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                PrimaryFloatingButton(
                    text = "Register",
                    onClick = { viewModel.register() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = true,
                )
                PrimaryFloatingButton(
                    text = "Deregister",
                    onClick = {
                        val authToDeregister = uiState.authToDeregister
                        authToDeregister?.let { viewModel.deregister(it) }
                            ?: Toast.makeText(
                                    context,
                                    "Please select one authenticator from the list.",
                                    Toast.LENGTH_LONG,
                                )
                                .show()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = true,
                )
            }
        }
    }

    BackHandler(true) { onNavigateUp() }
}

@Composable
fun AuthenticatorList(
    authList: List<Authenticator>,
    viewModel: AuthenticatorsViewModel,
    selectedIndex: Int,
) {

    val onItemClick = { index: Int ->
        viewModel.markAuthenticatorForDeregistration(authList[index], index)
    }
    LazyColumn(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(authList) { index, item: Authenticator ->
            AuthenticatorInfoCard(
                authenticator = item,
                index = index,
                selected = selectedIndex == index,
                onItemClick = onItemClick,
            )
        }
    }
}

@Composable
fun AuthenticatorInfoCard(
    authenticator: Authenticator,
    index: Int,
    selected: Boolean,
    onItemClick: (Int) -> Unit,
) {
    FloatingCard(onClick = { onItemClick(index) }, selected = selected) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = getBitmap(authenticator.icon ?: ""),
                contentDescription = "Authenticator icon",
                modifier = Modifier.size(60.dp).padding(end = 16.dp),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = authenticator.title ?: " ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = authenticator.description ?: " ",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    maxLines = 2,
                )
            }
        }
    }
}

private fun handleUiState(
    context: Context,
    uiState: AuthenticatorState,
    viewModel: AuthenticatorsViewModel,
    onNavigateToPasscode: () -> Unit,
    onNavigateToFingerprint: () -> Unit,
    onNavigateToFace: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToOOTP: () -> Unit,
) {
    if (uiState.registrationState.groupSelected) {
        when (uiState.registrationState.group?.getAuthenticator()?.aaid) {
            SRP_PASSCODE,
            PASSCODE -> {
                val passcodeController = viewModel.getPasscodeController()
                viewModel.setInProgress(false)
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
                viewModel.setInProgress(false)
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
                viewModel.setInProgress(false)
                if (faceController != null) {
                    onNavigateToFace()
                } else {
                    Toast.makeText(context, "Face controller is not available.", Toast.LENGTH_LONG)
                        .show()
                }
            }

            VOICE,
            ADOS_VOICE -> {
                val voiceController = viewModel.getVoiceController()
                viewModel.setInProgress(false)
                if (voiceController != null) {
                    onNavigateToVoice()
                } else {
                    Toast.makeText(context, "Voice controller is not available.", Toast.LENGTH_LONG)
                        .show()
                }
            }

            OOTP -> {
                val ootpController = viewModel.getOotpController()
                viewModel.setInProgress(false)
                if (ootpController != null) {
                    onNavigateToOOTP()
                } else {
                    Toast.makeText(context, "OOTP controller is not available.", Toast.LENGTH_LONG)
                        .show()
                }
            }

            SILENT -> {
                viewModel.registerSilent()
            }
        }
        viewModel.clearSelectedGroup()
    }
}
