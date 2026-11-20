package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme

/**
 * A small mono label over a big number, like "bonus points / 1,240".
 * Light mode: lime tile with black text. Dark mode: outlined tile with a lime number.
 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = AcidTheme.colors
    val text = if (c.isDark) c.muted else c.numberFg
    Column(
        modifier
            .background(c.numberBg, AcidShapes.field)
            .border(AcidShapes.outline, c.fg, AcidShapes.field)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = AcidTheme.type.mono, color = text)
        Text(value, style = AcidTheme.type.number, color = c.numberFg)
    }
}
