package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme

/** Left navigation for wide screens (the web portal). */
@Composable
internal fun SideBar(
    destinations: List<Destination>,
    selected: String,
    onSelect: (String) -> Unit,
    footer: @Composable () -> Unit,
) {
    val c = AcidTheme.colors
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(232.dp).fillMaxHeight().padding(horizontal = 18.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text("rent a car", style = AcidTheme.type.number.copy(fontSize = 20.sp), modifier = Modifier.padding(horizontal = 10.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                destinations.forEach { d ->
                    val on = d.key == selected
                    Pill(
                        text = d.label,
                        onClick = { onSelect(d.key) },
                        background = if (on) c.buttonBg else Color.Transparent,
                        content = if (on) c.buttonFg else c.fg,
                        border = Color.Transparent,
                        role = Role.Tab,
                        align = Alignment.Start,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            footer()
        }
        Box(Modifier.width(AcidShapes.outline).fillMaxHeight().background(c.fg))
    }
}
