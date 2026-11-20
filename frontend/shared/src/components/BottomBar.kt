package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme

/** Bottom navigation for narrow screens: text pills, the current one filled. */
@Composable
internal fun BottomBar(destinations: List<Destination>, selected: String, onSelect: (String) -> Unit) {
    val c = AcidTheme.colors
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Row(Modifier.fillMaxWidth().height(AcidShapes.outline).background(c.fg)) {}
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            destinations.forEach { d ->
                val on = d.key == selected
                Pill(
                    text = d.label,
                    onClick = { onSelect(d.key) },
                    background = if (on) c.buttonBg else Color.Transparent,
                    content = if (on) c.buttonFg else c.fg,
                    border = Color.Transparent,
                    role = Role.Tab,
                    padding = PaddingValues(horizontal = if (on) 14.dp else 6.dp),
                )
            }
        }
    }
}
