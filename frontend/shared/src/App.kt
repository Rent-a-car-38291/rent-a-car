package com.avans.rentacar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.avans.rentacar.components.AppShell
import com.avans.rentacar.components.Destination
import com.avans.rentacar.components.StatTile
import com.avans.rentacar.demo.CarSearchScreen
import com.avans.rentacar.theme.AcidTheme
import com.avans.rentacar.theme.ThemeMode
import org.jetbrains.compose.reload.DevelopmentEntryPoint

private val Destinations = listOf(
    Destination("find", "find"),
    Destination("bookings", "bookings"),
    Destination("cars", "my cars"),
    Destination("insights", "insights"),
    Destination("account", "account"),
)

@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    // shortcut: the mode is not remembered across launches yet, store it on the device with Account settings
    var mode by remember { mutableStateOf(ThemeMode.System) }
    var tab by remember { mutableStateOf("find") }
    AcidTheme(mode) {
        AppShell(
            destinations = Destinations,
            selected = tab,
            onSelect = { tab = it },
            sidebarFooter = { StatTile("bonus points", "1,240") },
        ) {
            CarSearchScreen(mode, onModeChange = { mode = it })
        }
    }
}
