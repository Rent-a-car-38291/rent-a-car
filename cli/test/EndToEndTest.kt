package com.example.cli

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.ktor.module
import kotlin.test.*

/** Drives the client through the real HTTP client against the real server and a real Postgres. */
class EndToEndTest {
    @Test
    fun `a new user registers, reserves a car, drives a trip and sees the leaderboard`() {
        val postgres = EmbeddedPostgres.start()
        val images = java.nio.file.Files.createTempDirectory("cli-e2e-images")
        val server = embeddedServer(Netty, applicationEnvironment {
            config = MapApplicationConfig(
                "database.url" to postgres.getJdbcUrl("postgres", "postgres"),
                "database.user" to "postgres",
                "database.password" to "",
                "images.directory" to images.toString(),
            )
        }, configure = { connector { port = 0 } }) { module() }
        server.start(wait = false)
        try {
            val port = runBlocking { server.engine.resolvedConnectors().first().port }

            // register (logs in) > Cars, List with nine skipped filters, back
            // > Reservations, Reserve car 1 on today (default) in the afternoon, back
            // > Trips, Start (reservation 1), End (reservation 1: 100 km, 2 hard accelerations, 0 brakes), back
            // > Insights, Leaderboard with the default limit, back > Log out > Quit
            val output = runScript(
                HttpApi("http://localhost:$port"),
                "1", "Sam", "sam@example.com", "secret123",
                "1", "1", "", "", "", "", "", "", "", "", "", "0",
                "3", "1", "1", "", "2", "0",
                "4", "1", "1", "2", "1", "100", "2", "0", "0",
                "5", "1", "", "0",
                "0", "0",
            )

            assertTrue("Registered! Your account id is" in output, output)
            assertTrue("licensePlate" in output, "the sample fleet is listed\n$output")
            assertTrue("Reserved: reservation #" in output, output)
            assertTrue("Trip started at" in output, output)
            assertTrue("pointsAwarded" in output, output)
            assertTrue("Sam" in output.substringAfter("name  bonusPoints"), "Sam is on the leaderboard\n$output")
            assertTrue("Logged out." in output, output)
        } finally {
            server.stop(0, 0)
            postgres.close()
        }
    }
}
