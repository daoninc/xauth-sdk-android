package com.daon.fido.sdk.sample.kt.capture.ootp

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.fido.sdk.sample.kt.ui.theme.PrimaryFloatingButton
import com.daon.sdk.authenticator.Extensions
import com.daon.sdk.authenticator.controller.OOTPController

/**
 * UI for the OOTP authentication process.
 *
 * If OTP_TRANSACTION_UI extension is true, shows transaction data input UI. If false, authenticates
 * silently without user input.
 *
 * @param onNavigateUp Callback function to handle navigation when OOTP authentication is complete.
 * @param ootpController The OOTP controller to use for the authentication process.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticateOOTPScreen(onNavigateUp: () -> Unit, ootpController: OOTPController) {

    val viewModel = hiltViewModel<OOTPViewModel>()
    val context = LocalContext.current
    val eventFlow = viewModel.eventFlow

    // Check if transaction UI is required
    val requiresTransactionUI =
        ootpController.configuration.getBooleanExtension(Extensions.OTP_TRANSACTION_UI, false)

    DisposableEffect(key1 = viewModel) {
        viewModel.onStart(ootpController)
        onDispose { viewModel.onStop() }
    }

    LaunchedEffect(viewModel) {
        eventFlow.collect { event ->
            when (event) {
                is OOTPUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is OOTPUiEvent.NavigateUp -> {
                    onNavigateUp()
                }
                is OOTPUiEvent.EnableRetry -> {
                    // Retry handled in respective content composables
                }
            }
        }
    }

    BackHandler(true) { onNavigateUp() }

    if (requiresTransactionUI) {
        TransactionInputContent(viewModel = viewModel, onNavigateUp = onNavigateUp)
    } else {
        SilentAuthenticateContent(viewModel = viewModel)
    }
}

/** Content for silent OOTP authentication (no transaction UI required). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SilentAuthenticateContent(viewModel: OOTPViewModel) {
    // Start authentication immediately
    LaunchedEffect(Unit) { viewModel.authenticate(null) }

    Scaffold(topBar = { AppTopAppBar(title = "OOTP Authentication") }) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .safeContentPadding()
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(bottom = 24.dp),
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Authenticating...",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 22.sp,
            )
        }
    }
}

/** Content for OOTP authentication with transaction data input. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionInputContent(viewModel: OOTPViewModel, onNavigateUp: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    var textValue by remember { mutableStateOf(TextFieldValue("")) }
    val inProgress by viewModel.inProgress.collectAsState()

    // Handle retry by clearing text
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is OOTPUiEvent.EnableRetry) {
                textValue = TextFieldValue("")
            }
        }
    }

    Scaffold(topBar = { AppTopAppBar(title = "Enter Transaction Code") }) { paddingValues ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .safeContentPadding()
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Enter the transaction code to authenticate",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                label = { Text("Transaction Code", color = MaterialTheme.colorScheme.onSurface) },
                value = textValue,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { textValue = it },
                maxLines = 1,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                keyboardOptions =
                    KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (!inProgress) {
                                submitAuthentication(textValue.text, viewModel)
                            }
                        }
                    ),
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryFloatingButton(
                text = "Authenticate",
                onClick = {
                    focusManager.clearFocus()
                    if (!inProgress) {
                        submitAuthentication(textValue.text, viewModel)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !inProgress,
            )
        }
    }
}

private fun submitAuthentication(transactionData: String, viewModel: OOTPViewModel) {
    val data = transactionData.ifBlank { null }
    viewModel.authenticate(data)
}
