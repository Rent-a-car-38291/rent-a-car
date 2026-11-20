package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidShapes
import com.avans.rentacar.theme.AcidTheme

/**
 * Outlined text field with a small mono label above it.
 * When [error] is set the outline turns thick and red (pink in dark mode) and the message shows below with a "!".
 */
@Composable
fun AcidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val c = AcidTheme.colors
    val t = AcidTheme.type
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = t.mono, color = c.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            textStyle = t.body.copy(color = c.fg),
            cursorBrush = SolidColor(c.fg),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (error != null) error(error) },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .heightIn(min = 54.dp)
                        .background(c.surface, AcidShapes.field)
                        .border(
                            if (error != null) AcidShapes.errorOutline else AcidShapes.outline,
                            if (error != null) c.error else c.fg,
                            AcidShapes.field,
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = t.body, color = c.muted)
                    inner()
                }
            },
        )
        if (error != null) {
            // Space Grotesk's "!" reads as a capital I, so the mark is drawn in Unbounded.
            val message = buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = t.number.fontFamily, fontWeight = FontWeight.Bold)) { append("!") }
                append(" $error")
            }
            Text(message, style = t.body.copy(fontSize = t.label.fontSize), color = c.error)
        }
    }
}
