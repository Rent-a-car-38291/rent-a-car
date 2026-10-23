package com.example.trips

import com.example.accounts.UserRepository
import com.example.cars.CarStatus
import com.example.cars.PostgresCarRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.reservations.Daypart
import com.example.reservations.ReservationRepository
import com.example.reservations.ZONE
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.*

class StartEndTripRoutesTest {
    private val car = PostgresCarRepository(TestDatabase.dataSource).allCars().first { it.status == CarStatus.Available }.id
    private val today = LocalDate.now(ZONE)

    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private class Renter(val id: Int, val token: String)

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        block()
    }

    private suspend fun HttpClient.signUp(email: String): Renter {
        val registration = post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"$email","password":"supersecret"}""")
        }
        val id = Json.parseToJsonElement(registration.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
        val login = post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"supersecret"}""")
        }
        return Renter(id, Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content)
    }

    // A reservation straight in the database, so a test can use any date; a test makes at most three per date.
    private var reservations = 0
    private fun reserve(renter: Renter, date: LocalDate = today): Int =
        ReservationRepository(TestDatabase.dataSource).reserve(car, renter.id, date, Daypart.entries[reservations++ % Daypart.entries.size])!!.id

    private suspend fun HttpClient.start(id: Any, token: String? = null): HttpResponse =
        post("/reservations/$id/trip/start") { token?.let { bearerAuth(it) } }

    private suspend fun HttpClient.end(id: Any, body: String = trip(10), token: String? = null): HttpResponse =
        post("/reservations/$id/trip/end") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.oneCall(id: Any, token: String): HttpResponse =
        post("/reservations/$id/trip") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(trip(10))
        }

    private suspend fun HttpClient.bonusPoints(renter: Renter): Int {
        val response = put("/accounts/${renter.id}") {
            bearerAuth(renter.token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"jan@example.com"}""")
        }
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["bonusPoints"]!!.jsonPrimitive.int
    }

    private fun trip(km: Int, accelerations: Int = 0, brakes: Int = 0) =
        """{"distanceKm":$km,"hardAccelerations":$accelerations,"hardBrakes":$brakes}"""

    @Test
    fun StartingATripOfTodayGives200WithTheStartTime() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)

        val response = client.start(reservation, jan.token)

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(reservation, json["reservationId"]!!.jsonPrimitive.int)
        assertTrue(java.time.Instant.parse(json["startedAt"]!!.jsonPrimitive.content).isBefore(java.time.Instant.now().plusSeconds(5)))
    }

    @Test
    fun StartingTwiceGives409() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        assertEquals(HttpStatusCode.OK, client.start(reservation, jan.token).status)

        assertEquals(HttpStatusCode.Conflict, client.start(reservation, jan.token).status)
    }

    @Test
    fun AReservationThatIsNotTodayCannotBeStarted() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.Conflict, client.start(reserve(jan, today.plusDays(1)), jan.token).status)
        assertEquals(HttpStatusCode.Conflict, client.start(reserve(jan, today.minusDays(1)), jan.token).status)
    }

    @Test
    fun AReservationThatAlreadyHasATripCannotBeStarted() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        assertEquals(HttpStatusCode.Created, client.oneCall(reservation, jan.token).status)

        assertEquals(HttpStatusCode.Conflict, client.start(reservation, jan.token).status)
    }

    @Test
    fun AStartedTripCannotBeCancelledAndCanStillBeEnded() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        client.start(reservation, jan.token)

        val cancel = client.delete("/reservations/$reservation") { bearerAuth(jan.token) }

        assertEquals(HttpStatusCode.Conflict, cancel.status)
        assertEquals(HttpStatusCode.Created, client.end(reservation, trip(10), jan.token).status)
    }

    @Test
    fun TheOneCallEndpointGives409ForAStartedTripAndNoPoints() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        client.start(reservation, jan.token)

        assertEquals(HttpStatusCode.Conflict, client.oneCall(reservation, jan.token).status)
        assertEquals(0, client.bonusPoints(jan))
        assertEquals(HttpStatusCode.Created, client.end(reservation, trip(10), jan.token).status)
    }

    @Test
    fun StartAndEndAnswer401403404And400() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val reservation = reserve(jan)

        assertEquals(HttpStatusCode.Unauthorized, client.start(reservation).status)
        assertEquals(HttpStatusCode.Unauthorized, client.end(reservation).status)
        assertEquals(HttpStatusCode.Forbidden, client.start(reservation, piet.token).status)
        assertEquals(HttpStatusCode.Forbidden, client.end(reservation, token = piet.token).status)
        assertEquals(HttpStatusCode.NotFound, client.start(999999, jan.token).status)
        assertEquals(HttpStatusCode.NotFound, client.end(999999, token = jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.start("abc", jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.end("abc", token = jan.token).status)
        // someone else's start didn't count: Jan can still start
        assertEquals(HttpStatusCode.OK, client.start(reservation, jan.token).status)
    }

    @Test
    fun EndingAStartedTripGives201WithDurationAndPoints() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        client.start(reservation, jan.token)

        val response = client.end(reservation, trip(100, accelerations = 2, brakes = 3), jan.token)

        assertEquals(HttpStatusCode.Created, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val trip = json["trip"]!!.jsonObject
        assertEquals(75, trip["score"]!!.jsonPrimitive.int)
        assertTrue(trip["durationMinutes"]!!.jsonPrimitive.int >= 1)
        assertNotNull(trip["startedAt"]!!.jsonPrimitive.contentOrNull)
        assertEquals(75, json["pointsAwarded"]!!.jsonPrimitive.int)
        assertEquals(75, client.bonusPoints(jan))
    }

    @Test
    fun EndingWithoutAStartGives409AndNoPoints() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.Conflict, client.end(reserve(jan), token = jan.token).status)
        assertEquals(0, client.bonusPoints(jan))
    }

    @Test
    fun EndingTwiceGives409AndThePointsCountOnce() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        client.start(reservation, jan.token)
        assertEquals(HttpStatusCode.Created, client.end(reservation, trip(10), jan.token).status)

        assertEquals(HttpStatusCode.Conflict, client.end(reservation, trip(10), jan.token).status)
        assertEquals(10, client.bonusPoints(jan))
    }

    @Test
    fun AnInvalidEndGives400AndTheTripStaysStartedSoItCanBeRetried() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        client.start(reservation, jan.token)

        assertEquals(HttpStatusCode.BadRequest, client.end(reservation, trip(0), jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.end(reservation, trip(10, brakes = -1), jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.end(reservation, """{"distanceKm":10}""", jan.token).status)
        assertEquals(0, client.bonusPoints(jan))
        assertEquals(HttpStatusCode.Created, client.end(reservation, trip(10), jan.token).status)
    }

    @Test
    fun TwoEndsAtOnceGiveExactlyOne201AndTheirPointsOnce() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reserve(jan)
        client.start(reservation, jan.token)

        val statuses = coroutineScope { (1..6).map { async { client.end(reservation, trip(10), jan.token).status } }.awaitAll() }

        assertEquals(1, statuses.count { it == HttpStatusCode.Created })
        assertEquals(5, statuses.count { it == HttpStatusCode.Conflict })
        assertEquals(10, client.bonusPoints(jan))
    }

    @Test
    fun TheOneCallEndpointStillWorksAndLeavesTheTimesEmpty() = withApp {
        val jan = client.signUp("jan@example.com")

        val response = client.oneCall(reserve(jan), jan.token)

        assertEquals(HttpStatusCode.Created, response.status)
        val trip = Json.parseToJsonElement(response.bodyAsText()).jsonObject["trip"]!!.jsonObject
        assertEquals(JsonNull, trip["startedAt"])
        assertEquals(JsonNull, trip["durationMinutes"])
    }

    @Test
    fun TheHistoryShowsTheDurationOfAnEndedTripAndNullForAOneCallTrip() = withApp {
        val jan = client.signUp("jan@example.com")
        val started = reserve(jan)
        client.start(started, jan.token)
        client.end(started, trip(10), jan.token)
        client.oneCall(reserve(jan), jan.token)

        val history = Json.parseToJsonElement(client.get("/trips") { bearerAuth(jan.token) }.bodyAsText()).jsonArray.map { it.jsonObject }

        assertEquals(2, history.size)
        assertEquals(1, history.count { it["durationMinutes"] is JsonNull && it["startedAt"] is JsonNull })
        assertEquals(1, history.count { (it["durationMinutes"] as? JsonPrimitive)?.intOrNull != null && it["startedAt"] !is JsonNull })
    }

    @Test
    fun TheDurationRunsFromTheStartToTheEnd() {
        val trips = TripRepository(TestDatabase.dataSource)
        val renter = withUser("duration@example.com")
        val reservation = ReservationRepository(TestDatabase.dataSource).reserve(car, renter, today.minusDays(3), Daypart.MORNING)!!.id
        val startedAt = OffsetDateTime.now().minusMinutes(90)
        assertNotNull(trips.start(reservation, startedAt))

        val (trip) = trips.record(reservation, renter, 10, 100, 10, endedAt = startedAt.plusMinutes(37))!!

        assertEquals(37, trip.durationMinutes)
        assertEquals(startedAt.toInstant().toString(), trip.startedAt)
    }

    private fun withUser(email: String) = UserRepository(TestDatabase.dataSource).register("Jan", email, "supersecret")!!.id
}
