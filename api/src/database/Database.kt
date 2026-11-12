package com.example.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import javax.sql.DataSource

fun Application.connectDatabase(): DataSource {
    val config = environment.config
    val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.property("database.url").getString()
        username = config.property("database.user").getString()
        password = config.property("database.password").getString()
    })
    monitor.subscribe(ApplicationStopped) { dataSource.close() }
    return dataSource
}
