package com.example.cli

import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader
import java.time.LocalDate
import kotlin.test.*

class ConsoleTest {
    private fun console(vararg lines: String, out: ByteArrayOutputStream = ByteArrayOutputStream()) =
        Console(BufferedReader(StringReader(lines.joinToString("\n") + "\n")), PrintStream(out, true))

    @Test
    fun `a date prompt asks again until the format is right`() {
        val out = ByteArrayOutputStream()

        val date = console("12-10-2026", "2026-13-40", "2026-10-12", out = out).date("Date")

        assertEquals(LocalDate.of(2026, 10, 12), date)
        assertEquals(2, Regex("Please use the format YYYY-MM-DD").findAll(out.toString()).count())
    }

    @Test
    fun `an empty answer takes the default and a number outside its range is refused`() {
        assertEquals(15000, console("").int("Km", 15000, min = 0))
        assertEquals(3, console("0", "6", "3").int("Stars", 5, 1, 5))
    }

    @Test
    fun `a decimal comma is accepted`() {
        assertEquals(12.5, console("12,5").double("Rate"))
    }

    @Test
    fun `a table lines up its columns and reaches into nested objects`() {
        val out = ByteArrayOutputStream()
        val rows = Json.parseToJsonElement("""[{"id":1,"location":{"latitude":51.5}},{"id":22,"location":{"latitude":4.0}}]""").jsonArray

        console(out = out).table(rows, listOf("id", "location.latitude"))

        assertEquals(listOf("  id  location.latitude", "  --  -----------------", "  1   51.5", "  22  4.0"), out.toString().lines().filter { it.isNotBlank() })
    }

    @Test
    fun `an empty list says so`() {
        val out = ByteArrayOutputStream()

        console(out = out).table(JsonArray(emptyList()))

        assertTrue("nothing to show" in out.toString())
    }
}
