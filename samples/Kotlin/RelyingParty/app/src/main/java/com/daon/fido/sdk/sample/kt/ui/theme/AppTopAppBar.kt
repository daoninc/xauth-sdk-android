package com.daon.fido.sdk.sample.kt.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Common TopAppBar component used throughout the app Provides consistent styling with black text on
 * white background
 *
 * @param title The title text to display in the TopAppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(title: String) {
    TopAppBar(
        colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
        title = {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
    )
}
