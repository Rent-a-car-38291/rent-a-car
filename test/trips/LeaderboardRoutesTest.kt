package com.example.trips

import com.example.accounts.UserRepository
import com.example.cars.PostgresCarRepository
import com.example.cars.AddCarRequest
import com.example.cars.BodyStyle
import com.example.cars.CarType
import com.example.cars.Location
import com.example.cars.Transmission
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import com.example.reservations.Daypart
import com.example.reservations.ReservationRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import kotlin.test.*

class LeaderboardRoutesTest {
    private val users = UserRepository(TestDatabase.dataSource)
    private val trips = TripRepository(TestDatabase.dataSource)
    private val reservations = ReservationRepository(TestDatabase.dataSource)
    private var day = 0L

    @BeforeTest
    fun reset() {
        OrganizationRepository(TestDatabase.dataSource).clear()
        users.clear() // and with the users their cars, reservations and trips
        day = 0
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        block()
    }

    private val car by lazy {
        val owner = users.register("Owner", "owner@example.com", "supersecret")!!
        PostgresCarRepository(TestDatabase.dataSource).addCar(
            AddCarRequest(CarType.BEV, "Test Motors", "LB-1", 1, 45.5, Location(52.3, 4.9), 4, BodyStyle.Sedan, 2024, 150, Transmission.Automatic, "Blue", capacity = 75.0, consumption = 16.2).toCar(owner.id),
        ).id
    }

    // Registers a user and gives them the points through a recorded trip, like the API does.
    private fun driver(name: String, points: Int) {
        val user = users.register(name, "${name.lowercase()}$day@example.com", "supersecret")!!
        if (points == 0) return
        val reservation = reservations.reserve(car, user.id, LocalDate.of(2026, 1, 1).plusDays(day++), Daypart.MORNING)!!
        trips.record(reservation.id, user.id, 10, 100, points)
    }

    private suspend fun HttpClient.leaderboard(query: String = ""): HttpResponse = get("/leaderboard$query")

    private suspend fun HttpResponse.entries() = Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject }

    @Test
    fun TheMostPointsComeFirstAndEqualPointsAreOrderedByName() = withApp {
        driver("Carl", 50)
        driver("Bob", 80)
        driver("Ann", 50)

        val response = client.leaderboard()

        assertEquals(HttpStatusCode.OK, response.status) // public: no token
        assertEquals(
            listOf("Bob" to 80, "Ann" to 50, "Carl" to 50),
            response.entries().map { it["name"]!!.jsonPrimitive.content to it["bonusPoints"]!!.jsonPrimitive.int },
        )
    }

    @Test
    fun TheLimitCutsTheListAndTheTopStaysFirst() = withApp {
        driver("Ann", 10)
        driver("Bob", 30)
        driver("Carl", 20)

        val names = client.leaderboard("?limit=2").entries().map { it["name"]!!.jsonPrimitive.content }

        assertEquals(listOf("Bob", "Carl"), names)
    }

    @Test
    fun OnlyTheNameAndThePointsAreShown() = withApp {
        driver("Ann", 10)

        val body = client.leaderboard().bodyAsText()

        assertEquals(setOf("name", "bonusPoints"), Json.parseToJsonElement(body).jsonArray.single().jsonObject.keys)
        assertFalse(body.contains("@"))
        assertFalse(body.contains("ann0@example.com"))
    }

    @Test
    fun UsersWithoutPointsAndTheSampleFleetAreNotListed() = withApp {
        driver("Ann", 10)
        driver("Zero", 0)

        val names = client.leaderboard().entries().map { it["name"]!!.jsonPrimitive.content }

        assertEquals(listOf("Ann"), names)
    }

    @Test
    fun WithoutAnyPointsTheListIsEmpty() = withApp {
        val response = client.leaderboard()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[]", response.bodyAsText())
    }

    @Test
    fun AnInvalidLimitGivesBadRequest() = withApp {
        for (limit in listOf("0", "101", "-1", "abc", "1.5", "")) {
            assertEquals(HttpStatusCode.BadRequest, client.leaderboard("?limit=$limit").status, "limit=$limit")
        }
    }
}
