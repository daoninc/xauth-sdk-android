package com.daon.fido.sdk.sample.kt.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes =
    Shapes(
        small = RoundedCornerShape(8.dp), // More rounded for modern look
        medium = RoundedCornerShape(12.dp), // Floating button effect
        large = RoundedCornerShape(16.dp), // Large components with rounded corners
    )
