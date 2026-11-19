package com.avans.rentacar.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalTextStyle

/** The person's choice in Account settings. [System] follows the device. */
enum class ThemeMode { System, Light, Dark }

private val LocalAcidColors = staticCompositionLocalOf { LightAcidColors }
private val LocalAcidType = staticCompositionLocalOf<AcidType> { error("Wrap the UI in AcidTheme") }

/** Read the current colors and type inside [AcidTheme]: `AcidTheme.colors.fg`. */
object AcidTheme {
    val colors: AcidColors
        @Composable @ReadOnlyComposable get() = LocalAcidColors.current
    val type: AcidType
        @Composable @ReadOnlyComposable get() = LocalAcidType.current
}

@Composable
fun AcidTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkAcidColors else LightAcidColors
    val type = acidType()
    MaterialTheme(colorScheme = colors.toMaterial(), typography = type.toMaterial(), shapes = AcidShapes.material) {
        CompositionLocalProvider(
            LocalAcidColors provides colors,
            LocalAcidType provides type,
            LocalContentColor provides colors.fg,
            LocalTextStyle provides type.body,
            content = content,
        )
    }
}

/** Maps our colors onto Material's slots, so the date picker, dialogs and sheets match. */
private fun AcidColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = buttonBg, onPrimary = buttonFg,
        primaryContainer = Acid, onPrimaryContainer = Ink,
        inversePrimary = buttonFg,
        secondary = fg, onSecondary = bg,
        secondaryContainer = Acid, onSecondaryContainer = Ink,
        tertiary = Violet, onTertiary = Ink,
        tertiaryContainer = Violet, onTertiaryContainer = Ink,
        background = bg, onBackground = fg,
        surface = surface, onSurface = fg,
        surfaceVariant = photo, onSurfaceVariant = muted,
        surfaceTint = surface,
        surfaceBright = surface, surfaceDim = bg,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = surface, surfaceContainerHighest = surface,
        inverseSurface = fg, inverseOnSurface = bg,
        outline = fg, outlineVariant = line,
        error = error, onError = onError,
        errorContainer = Pink, onErrorContainer = Ink,
        scrim = Ink,
    )
}
