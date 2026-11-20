package com.avans.rentacar.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidTheme

/**
 * Draws the wave motif across the full width, centered on [centerY].
 * [flipped] starts the wave going down instead of up, so stacked waves can cross.
 */
fun DrawScope.drawWave(
    color: Color,
    centerY: Float = size.height / 2,
    amplitude: Dp = 15.dp,
    wavelength: Dp = 150.dp,
    strokeWidth: Dp = 5.dp,
    flipped: Boolean = false,
) {
    val a = amplitude.toPx() * if (flipped) -1 else 1
    val w = wavelength.toPx()
    val path = Path().apply {
        moveTo(0f, centerY)
        var x = 0f
        while (x < size.width) {
            cubicTo(x + w / 3, centerY - 2 * a, x + 2 * w / 3, centerY + 2 * a, x + w, centerY)
            x += w
        }
    }
    drawPath(path, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))
}

/** A single wave as a section divider. One per screen in light mode. */
@Composable
fun WaveLine(
    modifier: Modifier = Modifier,
    color: Color = AcidTheme.colors.fg,
    strokeWidth: Dp = 3.dp,
) {
    Canvas(modifier.fillMaxWidth().height(24.dp)) {
        drawWave(color, amplitude = 6.dp, wavelength = 60.dp, strokeWidth = strokeWidth)
    }
}
