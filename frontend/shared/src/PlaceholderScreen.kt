package com.avans.rentacar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avans.rentacar.components.SecondaryButton
import com.avans.rentacar.theme.AcidTheme

/** Stands in for a page that is not built yet. [onBack] adds a back button. */
@Composable
fun PlaceholderScreen(title: String, note: String, onBack: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AcidTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) SecondaryButton("Back", onBack)
        Text(title, style = AcidTheme.type.headline)
        Text(note, color = AcidTheme.colors.muted)
    }
}
