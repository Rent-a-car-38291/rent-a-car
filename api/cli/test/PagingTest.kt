package com.example.cli

import kotlinx.serialization.json.Json
import kotlin.test.*

class PagingTest {
    private val login = "POST /login" to json("""{"token":"abc"}""")

    private fun car(id: Int) = """{"id":$id,"type":"ICE","brand":"Brand$id","licensePlate":"PL-$id","rate":25.0,"status":"Available","averageRating":null,"location":{"latitude":51.5,"longitude":4.7}}"""

    // Three cars in two pages of two: the server cuts the list on the offset and tells the whole size in the header.
    private fun pagedCars() = FakeApi().apply {
        handler = { call, query ->
            if (call != "GET /cars") null else {
                val offset = query["offset"]!!.toInt()
                val page = (1..3).drop(offset).take(2)
                ApiResponse(200, Json.parseToJsonElement("[${page.joinToString(",") { car(it) }}]"), total = 3)
            }
        }
    }

    @Test
    fun `a long list is shown page by page while the user asks for the next one`() {
        val api = pagedCars()

        // visitor > Cars > list (nine skipped filters), 'y' asks for the next page
        val output = runScript(api, "3", "1", "1", "", "", "", "", "", "", "", "", "", "y", "0", "0", "0")

        assertEquals(listOf("0", "2"), api.queries.map { it["offset"] })
        assertTrue("Showing 2 of 3." in output)
        assertTrue("Brand1" in output && "Brand2" in output && "Brand3" in output)
    }

    @Test
    fun `the next page is only offered when there is more and the user can stop`() {
        val api = pagedCars()

        val output = runScript(api, "3", "1", "1", "", "", "", "", "", "", "", "", "", "n", "0", "0", "0")

        assertEquals(1, api.queries.size)
        assertTrue("Brand2" in output)
        assertFalse("Brand3" in output)
    }

    @Test
    fun `a list that fits on one page does not ask`() {
        val api = FakeApi("GET /cars" to ApiResponse(200, Json.parseToJsonElement("[${car(1)}]"), total = 1))

        val output = runScript(api, "3", "1", "1", "", "", "", "", "", "", "", "", "", "0", "0", "0")

        assertFalse("Show the next page?" in output)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun `picking a car can ask for more cars and still returns the chosen car`() {
        val api = pagedCars()
        val ctx = Context(api, consoleOf("3", "1"), Session())

        // option 3 = 'More cars...', then car 3 of the second page (option 1)
        val id = ctx.pickCar()

        assertEquals(3, id)
        assertEquals(listOf("0", "2"), api.queries.map { it["offset"] })
    }

    @Test
    fun `an error body of the server is shown as its message`() {
        val api = FakeApi(login, "POST /logout" to status(204),
            "GET /cars/9/ratings" to ApiResponse(404, Json.parseToJsonElement("""{"error":"not_found","message":"Car with ID 9 not found"}""")))

        // Cars (1) > Car details, photos and ratings (2): pick nothing from the list, so call the route through the context instead
        val output = java.io.ByteArrayOutputStream()
        val ctx = Context(api, consoleOf(out = output), Session())

        assertNull(ctx.call("GET", "/cars/9/ratings"))
        assertTrue("! Car with ID 9 not found" in output.toString())
    }

    private fun consoleOf(vararg lines: String, out: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream()) =
        Console(java.io.BufferedReader(java.io.StringReader(lines.joinToString("\n") + "\n")), java.io.PrintStream(out, true))
}
