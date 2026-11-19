package com.avans.rentacar.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avans.rentacar.components.AcidChip
import com.avans.rentacar.components.AcidTextField
import com.avans.rentacar.components.SecondaryButton
import com.avans.rentacar.components.SidebarBreakpoint
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.ThemeMode
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsHorizontal
import io.github.lyxnx.compose.ui.tablericons.outline.Calendar

/** Static car search (page 4) with fake data. Only here to show the theme and components together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarSearchScreen(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    var daypart by remember { mutableStateOf("morning") }
    var kinds by remember { mutableStateOf(setOf("electric", "hydrogen")) }
    var distance by remember { mutableStateOf("abc") }
    val t = AcidTheme.type
    val c = AcidTheme.colors

    BoxWithConstraints {
        val wide = maxWidth >= SidebarBreakpoint
        LazyVerticalGrid(
            GridCells.Adaptive(if (wide) 230.dp else 320.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("find a car", style = if (wide) t.display else t.headline, modifier = Modifier.weight(1f))
                        val next = ThemeMode.entries[(mode.ordinal + 1) % ThemeMode.entries.size]
                        SecondaryButton("mode: ${mode.name.lowercase()}", onClick = { onModeChange(next) })
                    }
                    Text("free on", style = t.mono, color = c.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("wed 11 nov", onClick = {}, icon = TablerIcons.Outline.Calendar)
                        Dayparts.forEach { d -> AcidChip(d, selected = d == daypart, onClick = { daypart = d }) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Kinds.forEach { k -> AcidChip(k, selected = k in kinds, onClick = { kinds = if (k in kinds) kinds - k else kinds + k }) }
                    }
                    AcidTextField(
                        value = distance,
                        onValueChange = { distance = it },
                        label = "max distance from me, km",
                        error = if (distance.isNotEmpty() && distance.toIntOrNull() == null) "Enter a whole number of km." else null,
                        modifier = Modifier.widthIn(max = 360.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${FakeCars.size} cars free", style = t.mono, color = c.muted, modifier = Modifier.weight(1f))
                        SecondaryButton("filters (${kinds.size})", onClick = {}, accent = kinds.isNotEmpty(), icon = TablerIcons.Outline.AdjustmentsHorizontal)
                    }
                }
            }
            itemsIndexed(FakeCars) { i, car -> CarCard(car, i, if (wide) 130.dp else 104.dp, onClick = {}) }
        }
    }
}
