package com.example.cli

import kotlinx.serialization.json.Json
import kotlin.test.*

class ErrorMessageTest {
    @Test
    fun `every status code the API uses has its own friendly text`() {
        val texts = listOf(400, 401, 403, 404, 409, 413, 429, 500).map { errorMessage(it) }

        assertEquals(texts.size, texts.toSet().size)
        assertTrue(texts.none { it.startsWith("Unexpected") })
    }

    @Test
    fun `an unknown status is still explained`() {
        assertEquals("Unexpected answer from the server (HTTP 418).", errorMessage(418))
    }

    @Test
    fun `a hint for a status replaces the standard text and leaves the others alone`() {
        val hints = mapOf(409 to "That car is taken.")

        assertEquals("That car is taken.", errorMessage(409, hints))
        assertEquals(errorMessage(404), errorMessage(404, hints))
    }

    @Test
    fun `the message of the server is shown unless the action has a hint`() {
        assertEquals("Car with ID 5 not found", errorMessage(404, serverMessage = "Car with ID 5 not found"))
        assertEquals("That car is taken.", errorMessage(409, mapOf(409 to "That car is taken."), "The request conflicts with the current state."))
        assertEquals(errorMessage(404), errorMessage(404, serverMessage = null))
    }

    @Test
    fun `the message is read from the error body of the server`() {
        val body = Json.parseToJsonElement("""{"error":"not_found","message":"Car with ID 5 not found"}""")

        assertEquals("Car with ID 5 not found", ApiResponse(404, body).errorText)
        assertNull(ApiResponse(404, null).errorText)
        assertNull(ApiResponse(404, Json.parseToJsonElement("""{"error":"not_found","message":" "}""")).errorText)
        assertNull(ApiResponse(502, Json.parseToJsonElement("[]")).errorText)
    }

    @Test
    fun `an address without a scheme is reported instead of crashing`() {
        assertFailsWith<ServerUnreachable> { HttpApi("localhost:8080").call("GET", "/cars") }
    }
}
