package com.daon.fido.sdk.sample.kt.capture.ootp

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daon.fido.sdk.sample.kt.ui.theme.AppTopAppBar
import com.daon.sdk.authenticator.controller.OOTPController

/**
 * UI for the OOTP registration process (silent - no user input required).
 *
 * @param onNavigateUp Callback function to handle navigation when OOTP registration is complete.
 * @param ootpController The OOTP controller to use for the registration process.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterOOTPScreen(onNavigateUp: () -> Unit, ootpController: OOTPController) {

    val viewModel = hiltViewModel<OOTPViewModel>()
    val context = LocalContext.current
    val eventFlow = viewModel.eventFlow

    DisposableEffect(key1 = viewModel) {
        viewModel.onStart(ootpController)
        onDispose { viewModel.onStop() }
    }

    // Collect events - use viewModel as key for stability
    LaunchedEffect(viewModel) {
        Log.d("RegisterOOTPScreen", "BEFORE collect - about to subscribe")
        eventFlow.collect { event ->
            Log.d("RegisterOOTPScreen", "INSIDE collect - received event: $event")
            when (event) {
                is OOTPUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is OOTPUiEvent.NavigateUp -> {
                    onNavigateUp()
                }
                is OOTPUiEvent.EnableRetry -> {
                    viewModel.register()
                }
            }
        }
    }

    // Start registration immediately when screen loads
    LaunchedEffect(Unit) {
        Log.d("RegisterOOTPScreen", "BEFORE register() call")
        viewModel.register()
        Log.d("RegisterOOTPScreen", "AFTER register() call")
    }

    BackHandler(true) { onNavigateUp() }

    Scaffold(topBar = { AppTopAppBar(title = "OOTP Registration") }) { paddingValues ->
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
                text = "Registering OOTP...",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 22.sp,
            )
        }
    }
}
