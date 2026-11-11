package com.gfh.electricalworkbench

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Readable Daylight palette for the electrical tools; calculations are unchanged. */
internal object EWColors {
    val background = Color(0xFFF3F6FB)
    val raised = Color(0xFFFFFFFF)
    val card = Color(0xFFFFFFFF)
    val outline = Color(0xFF91A4B9)
    val primary = Color(0xFF124A7A)
    val onPrimary = Color.White
    val secondary = Color(0xFF1D4ED8)
    val warning = Color(0xFF874300)
    val text = Color(0xFF14263D)
    val muted = Color(0xFF465870)
    val good = Color(0xFF166534)
    val danger = Color(0xFFB42318)
}
@Composable
internal fun ElectricalUiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = EWColors.primary, onPrimary = EWColors.onPrimary,
        primaryContainer = Color(0xFFDCEBFA), onPrimaryContainer = EWColors.text,
        secondary = EWColors.secondary, onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5EDFC), onSecondaryContainer = EWColors.text,
        tertiary = EWColors.warning, onTertiary = Color.White,
        background = EWColors.background, onBackground = EWColors.text,
        surface = EWColors.raised, onSurface = EWColors.text,
        surfaceVariant = EWColors.card, onSurfaceVariant = EWColors.muted,
        outline = EWColors.outline, outlineVariant = Color(0xFFCFD9E6),
        error = EWColors.danger, onError = Color.White
    ), content = content)
}
