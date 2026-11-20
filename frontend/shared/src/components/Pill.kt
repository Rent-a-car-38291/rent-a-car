package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme

/**
 * The pill every button, chip and nav item is drawn with.
 * Kept internal: pages use [PrimaryButton], [SecondaryButton] or [AcidChip].
 */
@Composable
internal fun Pill(
    text: String,
    onClick: () -> Unit,
    background: Color,
    content: Color,
    border: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    role: Role = Role.Button,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp),
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = AcidShapes.minTouch, minHeight = AcidShapes.minTouch)
            .clip(AcidShapes.pill)
            .background(background)
            .border(AcidShapes.outline, border, AcidShapes.pill)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, align),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(text, style = AcidTheme.type.label, color = content, maxLines = 1)
    }
}
