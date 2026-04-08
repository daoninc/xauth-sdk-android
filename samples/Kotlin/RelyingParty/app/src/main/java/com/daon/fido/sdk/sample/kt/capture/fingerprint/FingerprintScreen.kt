package com.daon.fido.sdk.sample.kt.capture.fingerprint

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daon.fido.sdk.sample.kt.R
import com.daon.fido.sdk.sample.kt.ui.theme.ButtonColor
import com.daon.sdk.authenticator.controller.BiometricController

/** FingerprintScreen handles the UI for the fingerprint capture process. */
@Composable
fun FingerprintScreen(onNavigateUp: () -> Unit, biometricController: BiometricController) {
    val fingerprintViewModel = hiltViewModel<FingerprintViewModel>()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val eventFlow = fingerprintViewModel.eventFlow

    // Lock screen orientation
    // onStart is only called when lifecycle is actually RESUMED
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            fingerprintViewModel.onStart(context, biometricController)
        }
        // When repeatOnLifecycle exits (lifecycle dropped below RESUMED), stop capture
        fingerprintViewModel.onStop()
    }

    // Handle screen orientation lock
    DisposableEffect(Unit) {
        val activity = context as Activity
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is FingerprintUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is FingerprintUiEvent.NavigateUp -> {
                    onNavigateUp()
                }
            }
        }
    }

    // Handle back button press
    BackHandler { onNavigateUp() }

    // fingerprint capture UI
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(id = R.drawable.bg_fingerprint),
            contentDescription = "",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "Please touch sensor when ready",
            color = ButtonColor,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(10.dp),
        )
    }
}
