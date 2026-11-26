package com.avans.rentacar.components

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.avans.rentacar.theme.Acid
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.Ink

/**
 * An outlined pill. With [accent] it gets a lime fill and black text, for the one
 * thing on a page that is "yours or now", like "filters (2)" when filters are set.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = AcidTheme.colors
    if (accent) Pill(text, onClick, Acid, Ink, c.fg, modifier, enabled, icon)
    else Pill(text, onClick, Color.Transparent, c.fg, c.fg, modifier, enabled, icon)
}
