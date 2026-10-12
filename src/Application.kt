package org.jetbrains.amper.ktor

import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.cars.PostgresCarRepository
import com.example.cars.configureCars
import com.example.configureRouting
import com.example.configureSerialization
import com.example.database.connectDatabase
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureRouting()
    configureCars(PostgresCarRepository(connectDatabase()))
    configureSecurity()
    configureAccounts()
}
