package com.example.cli

import com.example.cli.areas.*

/** Every feature area, in menu order. A new area is one file in areas/ and one line here. */
val areas: List<Menu> = listOf(
    carsMenu,
    costsMenu,
    reservationsMenu,
    tripsMenu,
    insightsMenu,
    accountMenu,
    organizationsMenu,
)

val mainMenu = Menu("Main menu", areas.map(Menu::asItem), needsLogin = false)
