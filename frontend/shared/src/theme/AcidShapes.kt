package com.avans.rentacar.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shapes and line widths shared by every component. */
object AcidShapes {
    /** Buttons, chips, tags and nav items. */
    val pill = CircleShape
    val card = RoundedCornerShape(20.dp)
    /** Text fields, stat tiles, smaller cards. */
    val field = RoundedCornerShape(16.dp)
    /** Price badges on cards. */
    val badge = RoundedCornerShape(10.dp)
    val outline = 1.5.dp
    /** Outline of a field in its error state. */
    val errorOutline = 2.5.dp
    /** Smallest touch target on every platform. */
    val minTouch = 44.dp

    val material = Shapes(
        extraSmall = badge,
        small = badge,
        medium = field,
        large = card,
        extraLarge = card,
    )
}
