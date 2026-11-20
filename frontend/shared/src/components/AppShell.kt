package com.avans.rentacar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avans.rentacar.theme.AcidTheme

/** One of the five main places of the app. */
data class Destination(val key: String, val label: String)

/** From this width on, the bottom bar becomes a sidebar. */
val SidebarBreakpoint = 840.dp

/**
 * The frame around every page: a bottom bar on narrow screens, a sidebar on wide ones.
 * [sidebarFooter] only shows in the sidebar (for example the bonus points tile).
 */
@Composable
fun AppShell(
    destinations: List<Destination>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    sidebarFooter: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(AcidTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (maxWidth >= SidebarBreakpoint) {
            Row(Modifier.fillMaxSize()) {
                SideBar(destinations, selected, onSelect, sidebarFooter)
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { content() }
                BottomBar(destinations, selected, onSelect)
            }
        }
    }
}
