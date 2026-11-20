package com.avans.rentacar.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avans.rentacar.components.AcidChip
import com.avans.rentacar.components.AcidTextField
import com.avans.rentacar.components.OutlineCard
import com.avans.rentacar.components.PrimaryButton
import com.avans.rentacar.components.SecondaryButton
import com.avans.rentacar.components.StatTile
import com.avans.rentacar.components.StatusTag
import com.avans.rentacar.components.TagTone
import com.avans.rentacar.components.WaveLine
import com.avans.rentacar.components.drawWave
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.Pink
import com.avans.rentacar.theme.ThemeMode
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.outline.AdjustmentsHorizontal

/** Every component with its states, to check the look in light and dark. Not a page of the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComponentGallery(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    var chip by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("not an email") }
    val row = Arrangement.spacedBy(8.dp)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("components", style = AcidTheme.type.headline)
        val next = ThemeMode.entries[(mode.ordinal + 1) % ThemeMode.entries.size]
        SecondaryButton("mode: ${mode.name.lowercase()}", onClick = { onModeChange(next) })

        Section("buttons") {
            FlowRow(horizontalArrangement = row, verticalArrangement = row) {
                PrimaryButton("primary", onClick = {})
                SecondaryButton("secondary", onClick = {})
                SecondaryButton("accent (2)", onClick = {}, accent = true, icon = TablerIcons.Outline.AdjustmentsHorizontal)
                PrimaryButton("disabled", onClick = {}, enabled = false)
            }
        }
        Section("chips") {
            FlowRow(horizontalArrangement = row, verticalArrangement = row) {
                AcidChip("tap me", selected = chip, onClick = { chip = !chip })
                AcidChip("not selected", selected = false, onClick = {})
            }
        }
        Section("text fields") {
            Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AcidTextField(name, { name = it }, label = "name", placeholder = "your name")
                AcidTextField(email, { email = it }, label = "email", error = "Enter a valid email address.")
            }
        }
        Section("tags") {
            FlowRow(horizontalArrangement = row, verticalArrangement = row) {
                StatusTag("available")
                StatusTag("now", TagTone.Now)
                StatusTag("service due", TagTone.Attention)
                StatusTag("maintenance", TagTone.Off)
            }
        }
        Section("stat tile") { StatTile("bonus points", "1,240") }
        Section("card and waves") {
            val c = AcidTheme.colors
            OutlineCard(Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().height(104.dp).background(c.photo)) {
                    drawWave(c.acid, centerY = size.height * 0.75f)
                    drawWave(Pink, centerY = size.height * 0.88f)
                }
                Text("outline card", style = AcidTheme.type.title, modifier = Modifier.padding(14.dp))
            }
            WaveLine()
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AcidTheme.type.mono, color = AcidTheme.colors.muted)
        content()
    }
}
