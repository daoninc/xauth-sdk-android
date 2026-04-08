package com.daon.fido.sdk.sample.kt.util

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.daon.sdk.authenticator.util.Logger
import com.daon.sdk.xauth.core.Group
import com.daon.sdk.xauth.exts.ExtensionBuilder
import com.daon.sdk.xauth.model.AuthenticatorSet
import java.util.UUID

/**
 * Composable function to display a circular indeterminate progress bar.
 *
 * @param isDisplayed Boolean value to determine if the progress bar should be displayed.
 */
@Composable
fun CircularIndeterminateProgressBar(isDisplayed: Boolean) {
    if (isDisplayed) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(50.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

// Get the ImageBitmap from the base64 encoded string
fun getBitmap(icon: String): ImageBitmap {
    val options = BitmapFactory.Options()
    options.inMutable = true
    val commaIndex = icon.indexOf(',')
    val imageBase64 = icon.substring(commaIndex + 1)
    val imgBytes = Base64.decode(imageBase64, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size, options).asImageBitmap()
}

fun getGroupTitle(group: Group): String {
    val authSet: AuthenticatorSet = group.getAuthenticatorSet()
    return (0 until authSet.numberOfFactors).joinToString(", ") { index ->
        authSet.getAuthenticatorInfo(index).metadata().title ?: " "
    }
}

fun getGroupDescription(group: Group): String {
    val authSet: AuthenticatorSet = group.getAuthenticatorSet()
    return (0 until authSet.numberOfFactors).joinToString(", ") { index ->
        authSet.getAuthenticatorInfo(index).metadata().description ?: " "
    }
}

fun getFidoExtensions(prefs: SharedPreferences): Bundle {
    val isInjectionDetectionEnabled = prefs.getBoolean("injectionDetectionEnabled", true)
    val isSilentFingerprintRegistration = prefs.getBoolean("fingerprintSilentRegistration", false)
    val extensions =
        ExtensionBuilder()
            .invalidateFingerEnrollment(true)
            .injectionAttackDetection(isInjectionDetectionEnabled)
            .silentFingerprintRegistration(isSilentFingerprintRegistration)
            .deviceBiometricsTitle("Biometric login for my app")
            .deviceBiometricsSubtitle("Log in using your biometric credentials")
            .deviceBiometricsRegistrationReason("Use your fingerprint to enroll.")
            .deviceBiometricsAuthenticationReason("Use your fingerprint to log in securely.")
            .deviceBiometricsNegativeButtonText("Use another method")
            .build()
    return extensions
}

// Generate a random email for account creation
fun generateEmail(): String {
    val randomString = UUID.randomUUID().toString().substring(0, 15)
    Logger.logDebug(tag = "Utils", "generateRandomString :$randomString")
    return "$randomString@daon.com"
}
