package com.avans.rentacar.start

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.avans.rentacar.components.drawWave
import com.avans.rentacar.prefersReducedMotion
import com.avans.rentacar.theme.Acid
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.Ink
import com.avans.rentacar.theme.Pink
import com.avans.rentacar.theme.Violet

// Lime barely shows on the light background, so the first wave is ink there.
private fun introWaves(dark: Boolean) = listOf(if (dark) Acid else Ink, Pink, Violet)
private const val IntroMillis = 1000

/**
 * The first screen after the native splash: three waves draw in, then "rent a car" slides in.
 * About a second, a tap skips it. Follows the device theme, like the splash before it.
 */
@Composable
fun IntroScreen(onFinish: () -> Unit) {
    val reduced = prefersReducedMotion()
    val waves = introWaves(AcidTheme.colors.isDark)
    val t = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) kotlinx.coroutines.delay(IntroMillis.toLong())
        else t.animateTo(1f, tween(IntroMillis, easing = LinearEasing))
        onFinish()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(AcidTheme.colors.bg)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "skip", role = Role.Button, onClick = onFinish),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            waves.forEachIndexed { i, color ->
                // Wave i draws from 0.2 i to 0.2 i + 0.3 of the timeline: 0 to 0.7 for all three.
                val p = ((t.value - i * 0.2f) / 0.3f).coerceIn(0f, 1f)
                clipRect(right = size.width * p) {
                    drawWave(color, centerY = size.height / 2 + (i - 1) * 32.dp.toPx(), flipped = i == 1)
                }
            }
        }
        Text(
            "rent a car",
            style = AcidTheme.type.display,
            color = AcidTheme.colors.fg,
            modifier = Modifier.graphicsLayer {
                // The name slides in during the last 0.3 of the timeline.
                val name = ((t.value - 0.7f) / 0.3f).coerceIn(0f, 1f)
                alpha = name
                translationY = (1 - name) * 24.dp.toPx()
            },
        )
    }
}
