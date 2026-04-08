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
import androidx.compose.ui.focus.FocusDirection
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
 * UI for the passcode verification and re-enrollment process.
 *
 * @param onNavigateUp: Callback function to handle navigation when passcode verification and
 *   re-enrollment is complete.
 * @param passcodeController: The passcode controller to use for the passcode verification and
 *   re-enrollment process.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyAndReenrolPasscodeScreen(
    onNavigateUp: () -> Unit,
    passcodeController: PasscodeController,
) {

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val verifyAndReenrolPasscodeViewModel = hiltViewModel<PasscodeViewModel>()
    val eventFlow = verifyAndReenrolPasscodeViewModel.eventFlow

    var currentPasscode by remember { mutableStateOf(TextFieldValue("")) }
    var newPasscode by remember { mutableStateOf(TextFieldValue("")) }
    var confirmPasscode by remember { mutableStateOf(TextFieldValue("")) }

    DisposableEffect(key1 = verifyAndReenrolPasscodeViewModel) {
        verifyAndReenrolPasscodeViewModel.onStart(passcodeController)
        onDispose { verifyAndReenrolPasscodeViewModel.onStop() }
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
                    currentPasscode = TextFieldValue("")
                    newPasscode = TextFieldValue("")
                    confirmPasscode = TextFieldValue("")
                }
            }
        }
    }

    BackHandler(true) { onNavigateUp() }

    Scaffold(topBar = { AppTopAppBar(title = "Verify and Reenroll Passcode") }) { paddingValues ->
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
                text = "Enter your current passcode and choose a new one",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 22.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                label = { Text("Current Passcode", color = MaterialTheme.colorScheme.onSurface) },
                value = currentPasscode,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { currentPasscode = it },
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
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                keyboardActions =
                    KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                label = { Text("New Passcode", color = MaterialTheme.colorScheme.onSurface) },
                value = newPasscode,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { newPasscode = it },
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
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                keyboardActions =
                    KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                label = {
                    Text("Confirm New Passcode", color = MaterialTheme.colorScheme.onSurface)
                },
                value = confirmPasscode,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = { confirmPasscode = it },
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
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            validateAndChange(
                                currentPasscode.text,
                                newPasscode.text,
                                confirmPasscode.text,
                                context,
                                verifyAndReenrolPasscodeViewModel,
                                onReset = {
                                    currentPasscode = TextFieldValue("")
                                    newPasscode = TextFieldValue("")
                                    confirmPasscode = TextFieldValue("")
                                },
                            )
                        }
                    ),
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryFloatingButton(
                text = "Change Passcode",
                onClick = {
                    focusManager.clearFocus()
                    validateAndChange(
                        currentPasscode.text,
                        newPasscode.text,
                        confirmPasscode.text,
                        context,
                        verifyAndReenrolPasscodeViewModel,
                        onReset = {
                            currentPasscode = TextFieldValue("")
                            newPasscode = TextFieldValue("")
                            confirmPasscode = TextFieldValue("")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    currentPasscode.text.isNotEmpty() &&
                        newPasscode.text.isNotEmpty() &&
                        confirmPasscode.text.isNotEmpty(),
            )
        }
    }
}

private fun validateAndChange(
    currentPasscode: String,
    newPasscode: String,
    confirmPasscode: String,
    context: android.content.Context,
    viewModel: PasscodeViewModel,
    onReset: () -> Unit,
) {
    when {
        currentPasscode.isEmpty() -> {
            Toast.makeText(context, "Please enter your current passcode", Toast.LENGTH_SHORT).show()
        }
        newPasscode.isEmpty() -> {
            Toast.makeText(context, "Please enter a new passcode", Toast.LENGTH_SHORT).show()
        }
        newPasscode != confirmPasscode -> {
            onReset()
            Toast.makeText(context, "New passcodes do not match", Toast.LENGTH_SHORT).show()
        }
        else -> {
            viewModel.verifyAndReenrol(currentPasscode, newPasscode)
        }
    }
}
