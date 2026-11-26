package com.example.database

import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import javax.sql.DataSource

// One real Postgres per test run, started on first use and stopped by the library's shutdown hook. No Docker needed.
object TestDatabase {
    private val postgres: EmbeddedPostgres by lazy { EmbeddedPostgres.start() }

    val imagesDirectory: java.nio.file.Path by lazy { java.nio.file.Files.createTempDirectory("rent-a-car-images").also { it.toFile().deleteOnExit() } }

    val dataSource: DataSource get() = postgres.postgresDatabase

    /** An empty database of its own, for a test that must not share its rows with the others. */
    fun separate(name: String): DataSource {
        postgres.postgresDatabase.connection.use { c ->
            if (!c.createStatement().executeQuery("SELECT 1 FROM pg_database WHERE datname = '$name'").next()) c.createStatement().execute("CREATE DATABASE $name")
        }
        return postgres.getDatabase("postgres", name)
    }

    val config: ApplicationConfig
        get() = MapApplicationConfig(
            "database.url" to postgres.getJdbcUrl("postgres", "postgres"),
            "database.user" to "postgres",
            "database.password" to "",
            "images.directory" to imagesDirectory.toString(),
        )
}

fun ApplicationTestBuilder.useTestDatabase() = environment { config = TestDatabase.config }
