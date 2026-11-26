package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.Ink
import com.avans.rentacar.theme.Pink

enum class TagTone {
    /** A plain fact: "available", "reserved". */
    Neutral,
    /** Yours or happening now: lime. */
    Now,
    /** Needs attention: pink, always shown with a "!". */
    Attention,
    /** Out of use: "maintenance". */
    Off,
}

/** A small mono pill that shows a state. Not clickable. */
@Composable
fun StatusTag(text: String, tone: TagTone = TagTone.Neutral, modifier: Modifier = Modifier) {
    val c = AcidTheme.colors
    val (bg, fg, border) = when (tone) {
        TagTone.Neutral -> Triple(Color.Transparent, c.fg, c.fg)
        TagTone.Now -> Triple(c.buttonBg, c.buttonFg, c.buttonBg)
        TagTone.Attention -> Triple(Pink, Ink, Pink)
        TagTone.Off -> Triple(Color.Transparent, c.muted, c.muted)
    }
    Text(
        text = if (tone == TagTone.Attention && !text.startsWith("!")) "! $text" else text,
        style = AcidTheme.type.mono,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .background(bg, AcidShapes.pill)
            .border(AcidShapes.outline, border, AcidShapes.pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
