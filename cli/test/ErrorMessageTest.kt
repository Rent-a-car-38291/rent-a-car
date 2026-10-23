package com.example.cli

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
    fun `an address without a scheme is reported instead of crashing`() {
        assertFailsWith<ServerUnreachable> { HttpApi("localhost:8080").call("GET", "/cars") }
    }
}
