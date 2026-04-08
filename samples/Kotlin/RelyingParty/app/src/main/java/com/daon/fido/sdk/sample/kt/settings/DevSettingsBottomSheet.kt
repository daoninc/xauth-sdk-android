package com.daon.fido.sdk.sample.kt.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═════════════════════════════════════════════════════════════════════════════
// Overlay color scheme — contrasts with the current app theme so the developer
// panel is always visually distinct. Light theme → dark overlay, dark → light.
// ═════════════════════════════════════════════════════════════════════════════

/** Holds all overlay colors used by the developer settings bottom sheet. */
private data class OverlayColors(
    val sheetBackground: Color,
    val scrim: Color,
    val content: Color,
    val contentMuted: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val fieldBorder: Color,
    val fieldBorderFocused: Color,
)

/** Returns overlay colors that contrast with the current system theme. */
@Composable
private fun overlayColors(): OverlayColors {
    val isDark = isSystemInDarkTheme()
    val base = if (isDark) Color.White else Color.Black
    val accent = if (isDark) Color.Black else Color.White

    return OverlayColors(
        sheetBackground = base.copy(alpha = 0.85f),
        scrim = base.copy(alpha = 0.40f),
        content = accent,
        contentMuted = accent.copy(alpha = 0.70f),
        cardBackground = accent.copy(alpha = 0.10f),
        cardBorder = accent.copy(alpha = 0.30f),
        fieldBorder = accent.copy(alpha = 0.50f),
        fieldBorderFocused = accent,
    )
}

/**
 * Developer Settings bottom sheet. Displays app info (read-only), server configuration, OOTP
 * options, and biometric settings.
 *
 * Uses a translucent overlay background that contrasts with the current theme — dark overlay on
 * light theme, light overlay on dark theme — so the developer panel is always visually distinct.
 *
 * Server settings are edited locally and only persisted when the user taps "Save & Restart".
 * Immediate settings (OOTP, biometrics) are persisted on each toggle.
 *
 * @param isVisible Whether the bottom sheet is shown.
 * @param onDismiss Called when the sheet is dismissed.
 * @param devSettings Current settings loaded from prefs/config.
 * @param onServerSettingsSaveAndRestart Called with the edited settings when "Save & Restart" is
 *   tapped. The caller should persist and restart the app.
 * @param onImmediateSettingChange Called when a toggle/dropdown changes. Receives the pref key and
 *   new value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevSettingsBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    devSettings: DevSettings,
    onServerSettingsSaveAndRestart: (DevSettings) -> Unit,
    onImmediateSettingChange: (key: String, value: Any) -> Unit,
) {
    if (!isVisible) return

    val colors = overlayColors()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheetBackground,
        contentColor = colors.content,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null,
    ) {
        // Local copy of settings for server edits (not persisted until "Save & Restart")
        var localSettings by remember(devSettings) { mutableStateOf(devSettings) }

        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .height(screenHeight * 0.75f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp)
                    .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Developer Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.content,
                )
                Text(
                    text = "Swipe down or\ntap outside to close",
                    fontSize = 10.sp,
                    color = colors.contentMuted,
                    textAlign = TextAlign.End,
                    lineHeight = 14.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── App Info (read-only) ────────────────────────────────────────
            AppInfoSection(devSettings = devSettings, colors = colors)

            // ── Server Settings ─────────────────────────────────────────────
            ServerSection(
                localSettings = localSettings,
                onLocalSettingsChange = { localSettings = it },
                onSaveAndRestart = { onServerSettingsSaveAndRestart(localSettings) },
                colors = colors,
            )

            // ── OOTP ────────────────────────────────────────────────────────
            OotpSection(
                localSettings = localSettings,
                onLocalSettingsChange = { localSettings = it },
                onImmediateSettingChange = onImmediateSettingChange,
                colors = colors,
            )

            // ── Authentication ──────────────────────────────────────────────
            AuthenticationSection(
                localSettings = localSettings,
                onLocalSettingsChange = { localSettings = it },
                onImmediateSettingChange = onImmediateSettingChange,
                colors = colors,
            )

            // ── Biometrics ──────────────────────────────────────────────────
            BiometricsSection(
                localSettings = localSettings,
                onLocalSettingsChange = { localSettings = it },
                onImmediateSettingChange = onImmediateSettingChange,
                colors = colors,
            )

            // Bottom spacer for navigation bar clearance
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Sections
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun AppInfoSection(devSettings: DevSettings, colors: OverlayColors) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    SettingsSection(title = "App Info", colors = colors) {
        InfoRow(
            label = "Facet ID",
            value = devSettings.facetId,
            colors = colors,
            onCopy = {
                clipboardManager.setText(AnnotatedString(devSettings.facetId))
                Toast.makeText(context, "Facet ID copied", Toast.LENGTH_SHORT).show()
            },
        )
        InfoRow(label = "SDK Version", value = devSettings.sdkVersion, colors = colors)
        InfoRow(label = "App Version", value = devSettings.appVersion, colors = colors)
    }
}

@Composable
private fun ServerSection(
    localSettings: DevSettings,
    onLocalSettingsChange: (DevSettings) -> Unit,
    onSaveAndRestart: () -> Unit,
    colors: OverlayColors,
) {
    val fieldColors = overlayTextFieldColors(colors)

    SettingsSection(title = "Server", colors = colors) {
        // Service type selector
        ServiceTypeSelector(
            selected = localSettings.serviceType,
            onSelect = { onLocalSettingsChange(localSettings.copy(serviceType = it)) },
            colors = colors,
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (localSettings.serviceType) {
            ServiceType.RPSA -> {
                OverlayOutlinedTextField(
                    value = localSettings.rpsaServerUrl,
                    onValueChange = {
                        onLocalSettingsChange(localSettings.copy(rpsaServerUrl = it))
                    },
                    label = "RPSA Server URL",
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    fieldColors = fieldColors,
                )
            }
            ServiceType.REST -> {
                OverlayOutlinedTextField(
                    value = localSettings.restServerUrl,
                    onValueChange = {
                        onLocalSettingsChange(localSettings.copy(restServerUrl = it))
                    },
                    label = "REST Server URL",
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    fieldColors = fieldColors,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OverlayOutlinedTextField(
                    value = localSettings.restPath,
                    onValueChange = { onLocalSettingsChange(localSettings.copy(restPath = it)) },
                    label = "REST Path",
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    fieldColors = fieldColors,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OverlayOutlinedTextField(
                    value = localSettings.restAppId,
                    onValueChange = { onLocalSettingsChange(localSettings.copy(restAppId = it)) },
                    label = "App ID",
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    fieldColors = fieldColors,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OverlayOutlinedTextField(
                        value = localSettings.restRegPolicy,
                        onValueChange = {
                            onLocalSettingsChange(localSettings.copy(restRegPolicy = it))
                        },
                        label = "Reg Policy",
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        fieldColors = fieldColors,
                    )
                    OverlayOutlinedTextField(
                        value = localSettings.restAuthPolicy,
                        onValueChange = {
                            onLocalSettingsChange(localSettings.copy(restAuthPolicy = it))
                        },
                        label = "Auth Policy",
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        fieldColors = fieldColors,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OverlayOutlinedTextField(
                        value = localSettings.restUsername,
                        onValueChange = {
                            onLocalSettingsChange(localSettings.copy(restUsername = it))
                        },
                        label = "Username",
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        fieldColors = fieldColors,
                    )
                    OverlayOutlinedTextField(
                        value = localSettings.restPassword,
                        onValueChange = {
                            onLocalSettingsChange(localSettings.copy(restPassword = it))
                        },
                        label = "Password",
                        modifier = Modifier.weight(1f),
                        colors = colors,
                        fieldColors = fieldColors,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save & Restart button
        OutlinedButton(
            onClick = onSaveAndRestart,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.cardBackground,
                    contentColor = colors.content,
                ),
            border = BorderStroke(1.dp, colors.cardBorder),
            elevation =
                ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        ) {
            Text(text = "Save & Restart", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OotpSection(
    localSettings: DevSettings,
    onLocalSettingsChange: (DevSettings) -> Unit,
    onImmediateSettingChange: (key: String, value: Any) -> Unit,
    colors: OverlayColors,
) {
    val ootpModes = listOf("IdentifyWithOTP", "SignWithOTP")
    val fieldColors = overlayTextFieldColors(colors)

    SettingsSection(title = "OOTP", colors = colors) {
        // Mode dropdown
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = localSettings.ootpMode,
                onValueChange = {},
                readOnly = true,
                label = { Text("Generation Mode", color = colors.contentMuted) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                colors = fieldColors,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ootpModes.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode) },
                        onClick = {
                            onLocalSettingsChange(localSettings.copy(ootpMode = mode))
                            onImmediateSettingChange(DevSettingsKeys.OOTP_MODE, mode)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticationSection(
    localSettings: DevSettings,
    onLocalSettingsChange: (DevSettings) -> Unit,
    onImmediateSettingChange: (key: String, value: Any) -> Unit,
    colors: OverlayColors,
) {
    SettingsSection(title = "Authentication", colors = colors) {
        SwitchRow(
            label = "Confirmation OTP",
            checked = localSettings.confirmationOTP,
            onCheckedChange = {
                onLocalSettingsChange(localSettings.copy(confirmationOTP = it))
                onImmediateSettingChange(DevSettingsKeys.CONFIRMATION_OTP, it)
            },
            colors = colors,
        )
    }
}

@Composable
private fun BiometricsSection(
    localSettings: DevSettings,
    onLocalSettingsChange: (DevSettings) -> Unit,
    onImmediateSettingChange: (key: String, value: Any) -> Unit,
    colors: OverlayColors,
) {
    SettingsSection(title = "Biometrics", colors = colors) {
        SwitchRow(
            label = "Injection Detection",
            checked = localSettings.injectionDetection,
            onCheckedChange = {
                onLocalSettingsChange(localSettings.copy(injectionDetection = it))
                onImmediateSettingChange(DevSettingsKeys.INJECTION_DETECTION, it)
            },
            colors = colors,
        )
        SwitchRow(
            label = "Silent Fingerprint Registration",
            checked = localSettings.fingerprintSilentRegistration,
            onCheckedChange = {
                onLocalSettingsChange(localSettings.copy(fingerprintSilentRegistration = it))
                onImmediateSettingChange(DevSettingsKeys.FINGERPRINT_SILENT_REGISTRATION, it)
            },
            colors = colors,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Helper Composables
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSection(
    title: String,
    colors: OverlayColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.content,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = colors.cardBackground,
                contentColor = colors.content,
            ),
        border = BorderStroke(1.dp, colors.cardBorder),
    ) {
        Column(modifier = Modifier.padding(12.dp)) { content() }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    colors: OverlayColors,
    onCopy: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.contentMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onCopy != null) {
            TextButton(onClick = onCopy) {
                Text("Copy", fontSize = 12.sp, color = colors.contentMuted)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: OverlayColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.content)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = colors.content,
                    checkedTrackColor = colors.content.copy(alpha = 0.40f),
                    uncheckedThumbColor = colors.content.copy(alpha = 0.60f),
                    uncheckedTrackColor = colors.content.copy(alpha = 0.15f),
                    uncheckedBorderColor = colors.content.copy(alpha = 0.30f),
                ),
        )
    }
}

@Composable
private fun ServiceTypeSelector(
    selected: ServiceType,
    onSelect: (ServiceType) -> Unit,
    colors: OverlayColors,
) {
    Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        ServiceType.entries.forEach { type ->
            Row(
                modifier =
                    Modifier.weight(1f)
                        .selectable(
                            selected = (type == selected),
                            onClick = { onSelect(type) },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = (type == selected),
                    onClick = null,
                    colors =
                        RadioButtonDefaults.colors(
                            selectedColor = colors.content,
                            unselectedColor = colors.contentMuted,
                        ),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = type.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.content,
                )
            }
        }
    }
}

/** OutlinedTextField pre-styled for the translucent overlay background. */
@Composable
private fun OverlayOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    colors: OverlayColors,
    fieldColors: TextFieldColors,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.contentMuted) },
        modifier = modifier,
        singleLine = true,
        colors = fieldColors,
    )
}

/** Shared color set for OutlinedTextField on the overlay. */
@Composable
private fun overlayTextFieldColors(colors: OverlayColors) =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.content,
        unfocusedTextColor = colors.content,
        cursorColor = colors.content,
        focusedBorderColor = colors.fieldBorderFocused,
        unfocusedBorderColor = colors.fieldBorder,
        focusedLabelColor = colors.content,
        unfocusedLabelColor = colors.contentMuted,
        focusedTrailingIconColor = colors.content,
        unfocusedTrailingIconColor = colors.contentMuted,
    )
