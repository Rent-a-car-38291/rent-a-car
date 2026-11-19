package com.avans.rentacar.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avans.rentacar.components.OutlineCard
import com.avans.rentacar.components.StatusTag
import com.avans.rentacar.components.drawWave
import com.avans.rentacar.theme.Acid
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.Ink
import com.avans.rentacar.theme.Pink
import com.avans.rentacar.theme.Violet
import io.github.lyxnx.compose.ui.tablericons.TablerIcons
import io.github.lyxnx.compose.ui.tablericons.filled.Star

private val WavePairs = listOf(Acid to Pink, Violet to Acid, Pink to Violet)

@Composable
fun CarCard(car: FakeCar, index: Int, photoHeight: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AcidTheme.colors
    val t = AcidTheme.type
    OutlineCard(modifier, onClick = onClick) {
        Box(Modifier.fillMaxWidth().height(photoHeight).background(c.photo), contentAlignment = Alignment.Center) {
            val (top, bottom) = WavePairs[index % WavePairs.size]
            Canvas(Modifier.fillMaxSize()) {
                drawWave(top, centerY = size.height * 0.75f, flipped = index % 2 == 1)
                drawWave(bottom, centerY = size.height * 0.88f, flipped = index % 2 == 1)
            }
            Text("[car photo]", style = t.mono, color = c.muted)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(car.brand, style = t.title, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusTag(car.kind)
                    Text("${car.rating}", style = t.mono, color = c.muted)
                    Icon(TablerIcons.Filled.Star, contentDescription = "stars", tint = c.muted, modifier = Modifier.size(12.dp))
                    Text("· ${car.distanceKm} km", style = t.mono, color = c.muted)
                }
            }
            Text(
                "€${car.rate}",
                style = t.label.copy(fontSize = 15.sp),
                color = Ink,
                modifier = Modifier.background(Acid, AcidShapes.badge).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
