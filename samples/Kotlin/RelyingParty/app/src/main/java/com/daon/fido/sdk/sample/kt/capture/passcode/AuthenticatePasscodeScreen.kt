package com.daon.fido.sdk.sample.kt.capture.passcode

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.fido.sdk.sample.kt.ui.theme.PrimaryFloatingButton
import com.daon.sdk.authenticator.controller.PasscodeController

/**
 * UI for the passcode authentication process.
 *
 * @param onNavigateUp: Callback function to handle navigation when passcode authentication is
 *   complete.
 * @param passcodeController: The passcode controller to use for the passcode authentication
 *   process.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatePasscodeScreen(onNavigateUp: () -> Unit, passcodeController: PasscodeController) {

    val focusManager = LocalFocusManager.current
    val passcodeViewModel = hiltViewModel<PasscodeViewModel>()
    val context = LocalContext.current
    val eventFlow = passcodeViewModel.eventFlow
    var textValue1 by remember { mutableStateOf(TextFieldValue("")) }

    DisposableEffect(key1 = passcodeViewModel) {
        passcodeViewModel.onStart(passcodeController)
        onDispose { passcodeViewModel.onStop() }
    }

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is PasscodeUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is PasscodeUiEvent.NavigateUp -> {
                    onNavigateUp()
                }
                is PasscodeUiEvent.EnableRetry -> {
                    textValue1 = TextFieldValue("")
                }
            }
        }
    }

    BackHandler(true) { onNavigateUp() }

    Scaffold(topBar = { AppTopAppBar(title = "Enter Passcode") }) { paddingValues ->
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
                text = "Please enter your passcode to authenticate",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                label = { Text("Passcode", color = MaterialTheme.colorScheme.onSurface) },
                value = textValue1,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { textValue1 = it },
                maxLines = 1,
                visualTransformation = PasswordVisualTransformation(),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Password,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (textValue1.text.isNotEmpty()) {
                                passcodeViewModel.authenticate(textValue1.text)
                            } else {
                                Toast.makeText(
                                        context,
                                        "Please enter your passcode",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                        }
                    ),
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryFloatingButton(
                text = "Authenticate",
                onClick = {
                    focusManager.clearFocus()
                    if (textValue1.text.isNotEmpty()) {
                        passcodeViewModel.authenticate(textValue1.text)
                    } else {
                        Toast.makeText(context, "Please enter your passcode", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = textValue1.text.isNotEmpty(),
            )
        }
    }
}
