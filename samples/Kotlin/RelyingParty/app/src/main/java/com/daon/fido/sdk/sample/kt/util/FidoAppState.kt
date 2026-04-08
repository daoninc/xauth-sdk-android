package com.daon.fido.sdk.sample.kt.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope

/** Class to hold the state of the app. */
class FidoAppState(val navController: NavHostController)

/** Remember the state of the app. */
@Composable
fun rememberFidoAppState(
    navController: NavHostController = rememberNavController(),
    snackbarScope: CoroutineScope = rememberCoroutineScope(),
) = remember(navController, snackbarScope) { FidoAppState(navController = navController) }
