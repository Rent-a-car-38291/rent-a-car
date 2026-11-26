package com.avans.rentacar.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.avans.rentacar.resources.Res
import com.avans.rentacar.resources.space_grotesk_medium
import com.avans.rentacar.resources.space_grotesk_regular
import com.avans.rentacar.resources.space_mono_regular
import com.avans.rentacar.resources.unbounded_bold
import com.avans.rentacar.resources.unbounded_medium
import org.jetbrains.compose.resources.Font

/**
 * Type scale: Unbounded for tight lowercase headlines and numbers,
 * Space Grotesk for body and buttons, Space Mono for small labels.
 */
@Immutable
data class AcidType(
    /** Desktop page title, "find a car". */
    val display: TextStyle,
    /** Mobile page title. */
    val headline: TextStyle,
    /** Card and section titles, "tesla model 3". */
    val title: TextStyle,
    /** Big numbers in stat tiles. */
    val number: TextStyle,
    val body: TextStyle,
    /** Buttons, chips, nav items and prices. */
    val label: TextStyle,
    /** Small monospace labels: field names, counts, meta lines, tags. */
    val mono: TextStyle,
)

@Composable
fun acidType(): AcidType {
    val unbounded = FontFamily(
        Font(Res.font.unbounded_medium, FontWeight.Medium),
        Font(Res.font.unbounded_bold, FontWeight.Bold),
    )
    val grotesk = FontFamily(
        Font(Res.font.space_grotesk_regular, FontWeight.Normal),
        Font(Res.font.space_grotesk_medium, FontWeight.Medium),
    )
    val mono = FontFamily(Font(Res.font.space_mono_regular, FontWeight.Normal))
    return AcidType(
        display = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.05).em),
        headline = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.033).em),
        title = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.03).em),
        number = TextStyle(fontFamily = unbounded, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.045).em),
        body = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
        label = TextStyle(fontFamily = grotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
        mono = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    )
}

/** Puts our fonts into Material's slots so dialogs, sheets and the date picker match. */
fun AcidType.toMaterial(): Typography = Typography(
    displayLarge = display, displayMedium = display, displaySmall = headline,
    headlineLarge = headline, headlineMedium = headline.copy(fontSize = 24.sp, lineHeight = 28.sp), headlineSmall = title.copy(fontSize = 20.sp),
    titleLarge = title.copy(fontSize = 20.sp), titleMedium = title, titleSmall = label,
    bodyLarge = body, bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp), bodySmall = body.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = label, labelMedium = mono, labelSmall = mono.copy(fontSize = 11.sp),
)
