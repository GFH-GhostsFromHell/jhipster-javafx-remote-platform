package com.gfh.electricalworkbench
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared high-contrast palette. Changes visuals, never electrical calculations. */
internal object EWColors {
    val background=Color(0xFF0C1522)
    val raised=Color(0xFF142539)
    val card=Color(0xFF192D42)
    val outline=Color(0xFF68829B)
    val primary=Color(0xFF70DED3)
    val onPrimary=Color(0xFF062A31)
    val secondary=Color(0xFFA5C8FF)
    val warning=Color(0xFFFFD28C)
    val text=Color(0xFFF6FAFE)
    val muted=Color(0xFFCBD8E6)
    val good=Color(0xFF92E6C2)
    val danger=Color(0xFFFFAAA6)
}
@Composable
internal fun ElectricalUiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=darkColorScheme(
        primary=EWColors.primary,onPrimary=EWColors.onPrimary,
        primaryContainer=Color(0xFF214A52),onPrimaryContainer=EWColors.text,
        secondary=EWColors.secondary,onSecondary=EWColors.background,
        secondaryContainer=Color(0xFF2A3F59),onSecondaryContainer=EWColors.text,
        tertiary=EWColors.warning,onTertiary=Color(0xFF382900),
        background=EWColors.background,onBackground=EWColors.text,
        surface=EWColors.raised,onSurface=EWColors.text,
        surfaceVariant=EWColors.card,onSurfaceVariant=EWColors.muted,
        outline=EWColors.outline,error=EWColors.danger,onError=EWColors.background
    ),content=content)
}
