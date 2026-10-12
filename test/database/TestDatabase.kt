package com.example.database

import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource

// One real Postgres per test run, started on first use and stopped by the library's shutdown hook. No Docker needed.
object TestDatabase {
    private val postgres: EmbeddedPostgres by lazy { EmbeddedPostgres.start() }

    val dataSource: DataSource get() = postgres.postgresDatabase

    val config: ApplicationConfig
        get() = MapApplicationConfig(
            "database.url" to postgres.getJdbcUrl("postgres", "postgres"),
            "database.user" to "postgres",
            "database.password" to "",
        )
}

fun ApplicationTestBuilder.useTestDatabase() = environment { config = TestDatabase.config }
