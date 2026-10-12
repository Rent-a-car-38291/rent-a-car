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

// Runs a .sql file from resources/db; the file may hold several statements.
fun DataSource.runScript(name: String) {
    val sql = checkNotNull(Thread.currentThread().contextClassLoader.getResource("db/$name")) { "db/$name not found" }.readText()
    connection.use { it.createStatement().use { statement -> statement.execute(sql) } }
}
