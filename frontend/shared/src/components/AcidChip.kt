package com.avans.rentacar.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidTheme

/** A filter or choice chip. Selected uses the button colors, so it follows the lime rule in both modes. */
@Composable
fun AcidChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AcidTheme.colors
    Pill(
        text = text,
        onClick = onClick,
        background = if (selected) c.buttonBg else Color.Transparent,
        content = if (selected) c.buttonFg else c.fg,
        border = if (selected) c.buttonBg else c.line,
        modifier = modifier.semantics { this.selected = selected },
        role = Role.Checkbox,
        padding = PaddingValues(horizontal = 14.dp),
    )
}
