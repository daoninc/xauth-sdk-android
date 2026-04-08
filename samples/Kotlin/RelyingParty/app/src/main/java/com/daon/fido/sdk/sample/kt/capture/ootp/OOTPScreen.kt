package com.daon.fido.sdk.sample.kt.capture.ootp

import androidx.compose.runtime.Composable
import com.daon.sdk.authenticator.controller.OOTPController

/**
 * UI for the OOTP capture process.
 *
 * @param onNavigateUp Callback function to handle navigation when OOTP capture is complete.
 * @param ootpController The OOTP controller to use for the capture process.
 */
@Composable
fun OOTPScreen(onNavigateUp: () -> Unit, ootpController: OOTPController) {

    when {
        ootpController.configuration.isRegistration() -> {
            // Display the registration screen if in enrollment mode (silent operation)
            RegisterOOTPScreen(onNavigateUp = onNavigateUp, ootpController = ootpController)
        }
        else -> {
            // Display the authentication screen if in authentication mode (text input for
            // transaction data)
            AuthenticateOOTPScreen(onNavigateUp = onNavigateUp, ootpController = ootpController)
        }
    }
}
