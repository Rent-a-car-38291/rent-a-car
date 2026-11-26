package com.avans.rentacar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.avans.rentacar.api.Api
import com.avans.rentacar.api.ApiError
import com.avans.rentacar.api.Page
import com.avans.rentacar.cars.Car
import com.avans.rentacar.cars.CarQuery
import com.avans.rentacar.search.CarSearchScreen
import com.avans.rentacar.components.AppShell
import com.avans.rentacar.components.Destination
import com.avans.rentacar.components.StatTile
import com.avans.rentacar.gallery.ComponentGallery
import com.avans.rentacar.start.IntroScreen
import com.avans.rentacar.start.WelcomeScreen
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

/** Where the app is. No navigation library: one state in [App]. */
sealed interface Screen {
    data object Intro : Screen
    data object Welcome : Screen
    data object CarSearch : Screen
    data object CreateAccount : Screen
    data object LogIn : Screen
    data class CarDetail(val id: Int) : Screen
}

/** Where the intro goes: logged in straight to Car search, otherwise Welcome. */
fun afterIntro(loggedIn: Boolean): Screen = if (loggedIn) Screen.CarSearch else Screen.Welcome

@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    // shortcut: the mode is not remembered across launches yet, store it on the device with Account settings
    var mode by remember { mutableStateOf(ThemeMode.System) }
    var screen by remember { mutableStateOf<Screen>(Screen.Intro) }
    val toWelcome = { screen = Screen.Welcome }
    val api = remember { Api() }
    // Welcome's cars and count; it shows without them when the server can't be reached.
    var welcomeCars by remember { mutableStateOf<Page<Car>?>(null) }
    LaunchedEffect(api) {
        welcomeCars = try { api.cars(CarQuery(), limit = 14) } catch (e: ApiError) { null }
    }

    when (val s = screen) {
        Screen.Intro -> AcidTheme(mode) {
            // shortcut: no stored login yet, always goes to Welcome; check the token once login storage exists
            IntroScreen(onFinish = { screen = afterIntro(loggedIn = false) })
        }
        Screen.Welcome -> AcidTheme(mode) {
            WelcomeScreen(
                cars = welcomeCars?.items.orEmpty(),
                total = welcomeCars?.total,
                onCar = { screen = Screen.CarDetail(it) },
                onBrowse = { screen = Screen.CarSearch },
                onCreateAccount = { screen = Screen.CreateAccount },
                onLogIn = { screen = Screen.LogIn },
            )
        }
        Screen.CarSearch -> AcidTheme(mode) { CarSearch(api, mode, { mode = it }, onCar = { screen = Screen.CarDetail(it) }, onBack = toWelcome) }
        Screen.CreateAccount -> AcidTheme(mode) { PlaceholderScreen("create account", "Signing up comes here.", toWelcome) }
        Screen.LogIn -> AcidTheme(mode) { PlaceholderScreen("log in", "Logging in comes here.", toWelcome) }
        is Screen.CarDetail -> AcidTheme(mode) { PlaceholderScreen("car ${s.id}", "The car's details come here.", toWelcome) }
    }
}

/** Car search as a visitor. Until they are built, the other tabs show the component gallery. */
@Composable
private fun CarSearch(api: Api, mode: ThemeMode, onModeChange: (ThemeMode) -> Unit, onCar: (Int) -> Unit, onBack: (() -> Unit)?) {
    var tab by remember { mutableStateOf("find") }
    AppShell(
        destinations = Destinations,
        selected = tab,
        onSelect = { tab = it },
        sidebarFooter = { StatTile("bonus points", "1,240") },
    ) {
        if (tab == "find") CarSearchScreen(api, onCar, onBack)
        else ComponentGallery(mode, onModeChange)
    }
}
