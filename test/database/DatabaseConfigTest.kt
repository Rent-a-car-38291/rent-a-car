package com.example.database

import io.ktor.server.config.*
import kotlin.test.*

class DatabaseConfigTest {
    @Test
    fun DefaultsPointAtTheComposeDatabase() {
        val config = ConfigLoader.load("application.yaml")

        assertEquals("jdbc:postgresql://localhost:5432/rentacar", config.property("database.url").getString())
        assertEquals("rentacar", config.property("database.user").getString())
    }
}
