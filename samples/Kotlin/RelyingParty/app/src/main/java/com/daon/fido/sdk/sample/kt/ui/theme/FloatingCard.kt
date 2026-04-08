package com.daon.fido.sdk.sample.kt.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable floating card component used throughout the app Provides consistent styling with white
 * background, dark borders, and shadow elevation
 *
 * @param modifier Modifier to be applied to the card
 * @param onClick Optional click callback. If provided, makes the card clickable
 * @param selected Whether the card is in selected state (adds thicker border and more elevation)
 * @param content The content to be displayed inside the card
 */
@Composable
fun FloatingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.surfaceVariant // Light gray for selected
                    } else {
                        MaterialTheme.colorScheme.surface // White background
                    },
                contentColor = MaterialTheme.colorScheme.onSurface, // Black text
            ),
        shape = RoundedCornerShape(12.dp), // Consistent with button corner radius
        border =
            BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onBackground // Thick black border when selected
                    } else {
                        MaterialTheme.colorScheme.outline // Regular dark gray border
                    },
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (selected) 6.dp else 3.dp, // More elevation when selected
                pressedElevation = 8.dp,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Specialized floating card for content that needs more padding (like transaction details) */
@Composable
fun FloatingContentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                ),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface, // White background
                contentColor = MaterialTheme.colorScheme.onSurface, // Black text
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp), // More padding for content cards
            content = content,
        )
    }
}
