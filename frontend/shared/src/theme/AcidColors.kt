package com.avans.rentacar.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Ink is the black that sits on lime. It never changes with the mode (the lime rule). */
val Ink = Color(0xFF0B0B0B)
val Acid = Color(0xFFD4FF3A)
val Pink = Color(0xFFFF8AC4)
val Violet = Color(0xFFB9A9FF)

/**
 * The acid wave colors for one mode.
 *
 * The lime rule: [acid] is only a background behind [Ink], or text on black.
 * Use [buttonBg]/[buttonFg] for "selected" and main actions, they already respect it in both modes.
 * Pink means "needs attention" and always comes with a "!" and words.
 */
@Immutable
data class AcidColors(
    val bg: Color,
    val surface: Color,
    val fg: Color,
    val muted: Color,
    val line: Color,
    val buttonBg: Color,
    val buttonFg: Color,
    val photo: Color,
    /** Background of a stat tile number, transparent in dark mode. */
    val numberBg: Color,
    /** The stat tile number itself. */
    val numberFg: Color,
    val error: Color,
    val onError: Color,
    val isDark: Boolean,
) {
    val acid get() = Acid
    val pink get() = Pink
    val violet get() = Violet
}

val LightAcidColors = AcidColors(
    bg = Color(0xFFF4F2EA),
    surface = Color(0xFFFFFFFF),
    fg = Ink,
    muted = Color(0xFF5F5E5A),
    line = Color(0xFFD8D5C8),
    buttonBg = Ink,
    buttonFg = Acid,
    photo = Color(0xFFDEDBCF),
    numberBg = Acid,
    numberFg = Ink,
    error = Color(0xFFB00020),
    onError = Color.White,
    isDark = false,
)

val DarkAcidColors = AcidColors(
    bg = Ink,
    surface = Color(0xFF1A1A19),
    fg = Color(0xFFF4F2EA),
    muted = Color(0xFFA8A69C),
    line = Color(0xFF2E2E2C),
    buttonBg = Acid,
    buttonFg = Ink,
    photo = Color(0xFF26215C),
    numberBg = Color.Transparent,
    numberFg = Acid,
    error = Pink,
    onError = Ink,
    isDark = true,
)
