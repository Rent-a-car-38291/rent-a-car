package com.example.cli

import kotlin.test.*

class MenuTest {
    private val cars = json("""[{"id":1,"type":"ICE","brand":"Peugeot","licensePlate":"85-GHB-2","rate":25.0,"status":"Available","averageRating":null,"location":{"latitude":51.5,"longitude":4.7}}]""")
    private val login = "POST /login" to json("""{"token":"abc"}""")

    @Test
    fun `a visitor browses the cars and sees only the public menus`() {
        val api = FakeApi("GET /cars" to cars)

        // start: 3 = browse as visitor, 1 = Cars, 1 = list (four skipped filters), then back out of every menu
        val output = runScript(api, "3", "1", "1", "", "", "", "", "0", "0", "0")

        assertEquals(listOf("GET /cars"), api.calls)
        assertTrue("Peugeot" in output && "85-GHB-2" in output)
        assertFalse("Reservations" in output, "visitors do not see menus that need a login")
        assertTrue("Goodbye!" in output)
    }

    @Test
    fun `logging in shows the full menu, sends the token and logging out revokes it`() {
        val api = FakeApi(login, "POST /logout" to status(204))
        val session = Session()

        val output = runScript(api, "2", "sam@example.com", "secret123", "0", "0", session = session)

        assertEquals(listOf("POST /login", "POST /logout"), api.calls)
        assertTrue("Reservations" in output && "Organizations" in output)
        assertNull(api.token)
        assertFalse(session.loggedIn)
        assertTrue("Logged out." in output)
    }

    @Test
    fun `a wrong password shows a friendly message and stays on the start screen`() {
        val api = FakeApi("POST /login" to status(401))

        val output = runScript(api, "2", "sam@example.com", "wrongwrong", "0")

        assertTrue("Wrong email or password." in output)
        assertFalse("Welcome" in output)
    }

    @Test
    fun `registering logs in and remembers the account id`() {
        val api = FakeApi("POST /accounts" to ApiResponse(201, kotlinx.serialization.json.Json.parseToJsonElement("""{"id":7,"name":"Sam","email":"sam@example.com","bonusPoints":0}""")), login, "POST /logout" to status(204))
        val session = Session()

        val output = runScript(api, "1", "Sam", "sam@example.com", "secret123", "0", "0", session = session)

        assertTrue("Your account id is 7." in output)
        assertEquals(listOf("POST /accounts", "POST /login", "POST /logout"), api.calls)
    }

    @Test
    fun `back at a prompt cancels the action without calling the server`() {
        val api = FakeApi(login, "GET /cars" to cars, "POST /logout" to status(204))

        // Reservations (3) > Reserve (1) > pick car 1 > 'back' at the date prompt
        val output = runScript(api, "2", "a@b.c", "secret123", "3", "1", "1", "back", "0", "0", "0")

        assertTrue("(cancelled)" in output)
        assertTrue(api.calls.none { it.startsWith("POST /cars") })
    }

    @Test
    fun `reserving sends the date and daypart and keeps the reservation for the pickers`() {
        val api = FakeApi(
            login, "GET /cars" to cars, "POST /logout" to status(204),
            "POST /cars/1/reservations" to ApiResponse(201, kotlinx.serialization.json.Json.parseToJsonElement("""{"id":9,"carId":1,"userId":2,"date":"2030-01-02","daypart":"EVENING"}""")),
        )

        // car 1, date, daypart 3 = EVENING, then "My reservations of this session" (2)
        val output = runScript(api, "2", "a@b.c", "secret123", "3", "1", "1", "2030-01-02", "3", "2", "0", "0", "0")

        assertTrue("Reserved: reservation #9" in output)
        assertEquals("""{"date":"2030-01-02","daypart":"EVENING"}""", api.bodies.last { it != null }.toString())
        assertTrue(Regex("9\\s+1\\s+2030-01-02\\s+EVENING").containsMatchIn(output), output)
    }

    @Test
    fun `a conflict shows the hint of the action instead of the generic text`() {
        val api = FakeApi(login, "GET /cars" to cars, "POST /cars/1/reservations" to status(409), "POST /logout" to status(204))

        val output = runScript(api, "2", "a@b.c", "secret123", "3", "1", "1", "2030-01-02", "1", "0", "0", "0")

        assertTrue("That car is not free then" in output)
    }

    @Test
    fun `a server that is down gives a message instead of a stack trace`() {
        val api = object : Api {
            override var token: String? = null
            override fun call(method: String, path: String, query: Map<String, String?>, body: kotlinx.serialization.json.JsonElement?): ApiResponse =
                throw ServerUnreachable("Cannot reach the server at http://localhost:1")
        }

        val output = runScript(api, "3", "1", "1", "", "", "", "", "0", "0", "0")

        assertTrue("! Cannot reach the server" in output)
    }

    @Test
    fun `an invalid menu choice asks again and the end of the input quits`() {
        val output = runScript(FakeApi(), "9", "x")

        assertTrue("Please choose a number from the list." in output)
        assertTrue("Goodbye!" in output)
    }

    @Test
    fun `deleting the account returns to the start screen`() {
        val api = FakeApi(login, "DELETE /accounts/5" to status(204))
        val session = Session().apply { accountId = 5 }

        // Account (6) > Delete (3) > confirm, then the start screen is back: quit
        val output = runScript(api, "2", "a@b.c", "secret123", "6", "3", "y", "0", session = session)

        assertTrue("Account deleted." in output)
        assertFalse(session.loggedIn)
        assertNull(api.token)
    }
}
