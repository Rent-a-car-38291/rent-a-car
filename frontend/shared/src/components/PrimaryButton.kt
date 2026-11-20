package com.avans.rentacar.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.avans.rentacar.theme.AcidTheme

/** The main action of a page: black with lime text in light mode, lime with black text in dark mode. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = AcidTheme.colors
    Pill(text, onClick, c.buttonBg, c.buttonFg, c.buttonBg, modifier, enabled, icon)
}
