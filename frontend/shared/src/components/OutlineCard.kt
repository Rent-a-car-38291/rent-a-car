package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme

/** A surface card with a 1.5 dp outline in the text color. Clickable when [onClick] is set. */
@Composable
fun OutlineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AcidTheme.colors
    Column(
        modifier = modifier
            .clip(AcidShapes.card)
            .background(c.surface)
            .border(AcidShapes.outline, c.fg, AcidShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}
