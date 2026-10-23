package com.example.cli

import kotlin.test.*

class MenuTest {
    private val cars = json("""[{"id":1,"type":"ICE","brand":"Peugeot","licensePlate":"85-GHB-2","rate":25.0,"status":"Available","averageRating":null,"location":{"latitude":51.5,"longitude":4.7}}]""")
    private val login = "POST /login" to json("""{"token":"abc"}""")

    @Test
    fun `a visitor browses the cars and sees only the public menus`() {
        val api = FakeApi("GET /cars" to cars)

        // start: 3 = browse as visitor, 1 = Cars, 1 = list (nine skipped filters), then back out of every menu
        val output = runScript(api, "3", "1", "1", "", "", "", "", "", "", "", "", "", "0", "0", "0")

        assertEquals(listOf("GET /cars"), api.calls)
        assertTrue("Peugeot" in output && "85-GHB-2" in output)
        assertFalse("Reservations" in output, "visitors do not see menus that need a login")
        assertTrue("Goodbye!" in output)
    }

    @Test
    fun `the car list sends the new filters and sort, matched in any case, and asks again for a wrong value`() {
        val api = FakeApi("GET /cars" to cars)

        // type, max rate, date, latitude skipped; body style 'suv' (any case), transmission 'cvt' is refused and asked again, 'automatic';
        // fuel skipped, minimum range 400, sort '-costPerKm'
        val output = runScript(api, "3", "1", "1", "", "", "", "", "suv", "cvt", "automatic", "", "400", "-costperkm", "0", "0", "0")

        assertTrue("Please choose one of: Manual, Automatic." in output)
        val query = api.queries.single()
        assertEquals("SUV", query["bodyStyle"])
        assertEquals("Automatic", query["transmission"])
        assertNull(query["fuelType"])
        assertEquals("400.0", query["minRange"])
        assertEquals("-costPerKm", query["sort"])
    }

    @Test
    fun `a location adds the longitude and distance prompts before the new filters`() {
        val api = FakeApi("GET /cars" to cars)

        // latitude 51.5, longitude 4.7, distance 10, then the five new filters skipped except the sort 'rate'
        val output = runScript(api, "3", "1", "1", "", "", "", "51.5", "4.7", "10", "", "", "", "", "rate", "0", "0", "0")

        val query = api.queries.single()
        assertEquals(mapOf("lat" to "51.5", "lon" to "4.7", "maxDistanceKm" to "10.0", "sort" to "rate"), query.filterValues { it != null })
        assertTrue("Peugeot" in output)
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

        // car 1, date, daypart 3 = EVENING; then trips (4) > start (1) offers the reservation of this run
        val output = runScript(api, "2", "a@b.c", "secret123", "3", "1", "1", "2030-01-02", "3", "0", "4", "1", "0", "0", "0")

        assertTrue("Reserved: reservation #9" in output)
        assertEquals("""{"date":"2030-01-02","daypart":"EVENING"}""", api.bodies.first { it != null && it.toString().contains("daypart") }.toString())
        assertTrue("#9 car 1 on 2030-01-02 EVENING" in output, output)
    }

    @Test
    fun `the fleet report of an organization is shown as a table and a 403 gets its own hint`() {
        val report = json("""[{"carId":4,"brand":"Tesla","licensePlate":"EV-1","reservations":3,"trips":2,"totalKm":40,"averageScore":80.5,"tcoPerYear":850.5}]""")
        val shown = runScript(FakeApi(login, "POST /logout" to status(204), "GET /organizations/7/report" to report), "2", "a@b.c", "secret123", "7", "7", "7", "0", "0", "0")
        val refused = runScript(FakeApi(login, "POST /logout" to status(204), "GET /organizations/7/report" to status(403)), "2", "a@b.c", "secret123", "7", "7", "7", "0", "0", "0")

        // Organizations (7) > Fleet report (7), organization id 7
        assertTrue(Regex("4\\s+Tesla\\s+EV-1\\s+3\\s+2\\s+40\\s+80.5\\s+850.5").containsMatchIn(shown), shown)
        assertTrue("Only members of the organization can see its report." in refused, refused)
    }

    @Test
    fun `my reservations lists them from the server with an optional status filter`() {
        val api = FakeApi(
            login, "POST /logout" to status(204),
            "GET /reservations" to json("""[{"id":9,"carId":1,"userId":2,"date":"2030-01-02","daypart":"EVENING","status":"CANCELLED"}]"""),
        )

        // Reservations (3) > My reservations (2), status CANCELLED
        val output = runScript(api, "2", "a@b.c", "secret123", "3", "2", "cancelled", "0", "0", "0")

        assertEquals(mapOf("status" to "CANCELLED"), api.queries[api.calls.indexOf("GET /reservations")])
        assertTrue(Regex("9\\s+1\\s+2030-01-02\\s+EVENING\\s+CANCELLED").containsMatchIn(output), output)
    }

    @Test
    fun `my reservations without a status asks the server for all of them and explains a 400`() {
        val api = FakeApi(login, "POST /logout" to status(204), "GET /reservations" to status(400))

        val output = runScript(api, "2", "a@b.c", "secret123", "3", "2", "", "0", "0", "0")

        assertEquals(mapOf("status" to null), api.queries[api.calls.indexOf("GET /reservations")])
        assertTrue("The status must be RESERVED, COMPLETED or CANCELLED." in output)
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

        val output = runScript(api, "3", "1", "1", "", "", "", "", "", "", "", "", "", "0", "0", "0")

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

    @Test
    fun `comparing car types shows the cost and CO2 per type`() {
        val api = FakeApi("GET /cars/compare" to json("""{"distanceKm":100,"currency":"EUR","types":[{"type":"BEV","cars":2,"averageCost":5.25,"co2Kg":0.0},{"type":"ICE","cars":3,"averageCost":13.0,"co2Kg":13.8}]}"""))

        // visitor > Costs (2) > Compare (3, after cost per km and quote) with the default distance
        val output = runScript(api, "3", "2", "3", "", "0", "0", "0")

        assertEquals(listOf("GET /cars/compare"), api.calls)
        assertEquals("100.0", api.queries.single()["distanceKm"])
        assertTrue(Regex("ICE\\s+3\\s+13.0\\s+13.8").containsMatchIn(output), output)
    }

    @Test
    fun `a car can be marked as serviced and a refusal shows a friendly message`() {
        val api = FakeApi(login, "GET /cars" to cars, "PUT /cars/1/service" to status(204), "POST /logout" to status(204))

        // log in, Cars (1), mark as serviced (7), the first car, back out of every menu
        val output = runScript(api, "2", "sam@example.com", "secret123", "1", "7", "1", "0", "0")

        assertTrue("PUT /cars/1/service" in api.calls)
        assertTrue("marked as serviced" in output)

        val refused = FakeApi(login, "GET /cars" to cars, "PUT /cars/1/service" to status(403), "POST /logout" to status(204))
        val refusedOutput = runScript(refused, "2", "sam@example.com", "secret123", "1", "7", "1", "0", "0")
        assertFalse("marked as serviced" in refusedOutput)
        assertTrue("PUT /cars/1/service" in refused.calls)
    }
}
