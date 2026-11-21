package com.example

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class CorsTest {
    private fun corsApp(development: Boolean, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        serverConfig { developmentMode = development }
        application {
            configureCors(listOf("https://portal.example.com"))
            routing { get("/cars") { call.response.header("X-Total-Count", "3"); call.respondText("[]") } }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.preflight(origin: String) = client.options("/cars") {
        header(HttpHeaders.Origin, origin)
        header(HttpHeaders.AccessControlRequestMethod, "GET")
        header(HttpHeaders.AccessControlRequestHeaders, "Authorization")
    }

    @Test
    fun AConfiguredOriginMayCallTheApi() = corsApp(development = false) {
        val response = preflight("https://portal.example.com")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("https://portal.example.com", response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun AnyLocalhostPortMayCallTheApiInDevelopmentMode() = corsApp(development = true) {
        assertEquals("http://localhost:8081", preflight("http://localhost:8081").headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals("http://127.0.0.1:3000", preflight("http://127.0.0.1:3000").headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun LocalhostIsRefusedOutsideDevelopmentMode() = corsApp(development = false) {
        assertEquals(HttpStatusCode.Forbidden, preflight("http://localhost:8081").status)
    }

    @Test
    fun AnUnknownOriginIsRefused() = corsApp(development = true) {
        assertEquals(HttpStatusCode.Forbidden, preflight("https://evil.example.com").status)
        // A look-alike that only starts with localhost is not this machine.
        assertEquals(HttpStatusCode.Forbidden, preflight("http://localhost.evil.example.com").status)
    }

    @Test
    fun ThePortalCanReadTheTotalCount() = corsApp(development = true) {
        val response = client.get("/cars") { header(HttpHeaders.Origin, "http://localhost:8081") }
        assertTrue(response.headers[HttpHeaders.AccessControlExposeHeaders].orEmpty().contains("X-Total-Count", ignoreCase = true))
    }

    @Test
    fun OriginsAreReadFromACommaSeparatedList() {
        assertEquals(listOf("https://a.example.com", "https://b.example.com"), parseOrigins(" https://a.example.com/, https://b.example.com ,"))
        assertEquals(emptyList(), parseOrigins(""))
    }
}
