package com.example.reservations

import com.example.accounts.UserRepository
import com.example.cars.CarStatus
import com.example.cars.PostgresCarRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.trips.TripRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import kotlin.test.*

class ReservationStatusRoutesTest {
    private val repository = ReservationRepository(TestDatabase.dataSource)
    private val cars = PostgresCarRepository(TestDatabase.dataSource).allCars().filter { it.status == CarStatus.Available }
    private val car = cars[0].id
    private val otherCar = cars[1].id
    private val today = LocalDate.now(ZONE)
    private val tomorrow = today.plusDays(1)

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

    private suspend fun HttpClient.list(query: String = "", token: String? = null): HttpResponse =
        get("/reservations$query") { token?.let { bearerAuth(it) } }

    private suspend fun HttpResponse.reservations() = Json.parseToJsonElement(bodyAsText()).jsonArray.map { it.jsonObject }

    private suspend fun HttpClient.reserve(carId: Int, date: LocalDate, daypart: String, token: String) = post("/cars/$carId/reservations") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody("""{"date":"$date","daypart":"$daypart"}""")
    }

    private suspend fun HttpClient.cancel(id: Int, token: String) = delete("/reservations/$id") { bearerAuth(token) }

    private fun trip(km: Int = 10) = """{"distanceKm":$km,"hardAccelerations":0,"hardBrakes":0}"""

    @Test
    fun ANewReservationIsReservedAndThatShowsInTheResponse() = withApp {
        val jan = client.signUp("jan@example.com")

        val response = client.reserve(car, tomorrow, "MORNING", jan.token)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("RESERVED", Json.parseToJsonElement(response.bodyAsText()).jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun TheListNeedsALoginAndShowsOnlyYourOwnNewestFirst() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val first = repository.reserve(car, jan.id, tomorrow, Daypart.MORNING)!!
        val second = repository.reserve(car, jan.id, tomorrow, Daypart.EVENING)!!
        val later = repository.reserve(otherCar, jan.id, tomorrow.plusDays(3), Daypart.MORNING)!!
        repository.reserve(otherCar, piet.id, tomorrow, Daypart.MORNING)

        assertEquals(HttpStatusCode.Unauthorized, client.list().status)
        val mine = client.list(token = jan.token).reservations()

        assertEquals(listOf(later.id, second.id, first.id), mine.map { it["id"]!!.jsonPrimitive.int })
        assertTrue(mine.all { it["userId"]!!.jsonPrimitive.int == jan.id })
    }

    @Test
    fun TheListFiltersOnStatusAndPages() = withApp {
        val jan = client.signUp("jan@example.com")
        val reserved = repository.reserve(car, jan.id, tomorrow, Daypart.MORNING)!!
        val cancelled = repository.reserve(car, jan.id, tomorrow, Daypart.AFTERNOON)!!
        val completed = repository.reserve(car, jan.id, today.minusDays(1), Daypart.MORNING)!!
        repository.cancel(cancelled.id)
        TripRepository(TestDatabase.dataSource).record(completed.id, jan.id, 10, 100, 10)

        fun ids(rows: List<JsonObject>) = rows.map { it["id"]!!.jsonPrimitive.int }
        assertEquals(listOf(reserved.id), ids(client.list("?status=RESERVED", jan.token).reservations()))
        assertEquals(listOf(cancelled.id), ids(client.list("?status=CANCELLED", jan.token).reservations()))
        assertEquals(listOf(completed.id), ids(client.list("?status=COMPLETED", jan.token).reservations()))
        assertEquals(listOf(cancelled.id, reserved.id), ids(client.list("?limit=2", jan.token).reservations()))
        assertEquals(listOf(completed.id), ids(client.list("?limit=2&offset=2", jan.token).reservations()))
    }

    @Test
    fun InvalidListParametersGive400() = withApp {
        val jan = client.signUp("jan@example.com")

        listOf("?status=DONE", "?status=reserved", "?limit=0", "?limit=101", "?limit=x", "?offset=-1").forEach { query ->
            assertEquals(HttpStatusCode.BadRequest, client.list(query, jan.token).status, query)
        }
    }

    @Test
    fun CancellingKeepsTheReservationAsCancelledAndFreesTheDaypart() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val reservation = repository.reserve(car, jan.id, tomorrow, Daypart.MORNING)!!
        assertEquals(HttpStatusCode.Conflict, client.reserve(car, tomorrow, "MORNING", piet.token).status)

        assertEquals(HttpStatusCode.NoContent, client.cancel(reservation.id, jan.token).status)

        val kept = client.list("?status=CANCELLED", jan.token).reservations().single()
        assertEquals(reservation.id, kept["id"]!!.jsonPrimitive.int)
        assertEquals(setOf(Daypart.MORNING, Daypart.AFTERNOON, Daypart.EVENING), (repository.reservedDayparts(car, tomorrow) + Daypart.entries).toSet())
        assertTrue(Daypart.MORNING !in repository.reservedDayparts(car, tomorrow))
        assertEquals(HttpStatusCode.Created, client.reserve(car, tomorrow, "MORNING", piet.token).status)
        // the new reservation is the only active one: a third renter is refused again
        val third = client.signUp("klaas@example.com")
        assertEquals(HttpStatusCode.Conflict, client.reserve(car, tomorrow, "MORNING", third.token).status)
    }

    @Test
    fun ACancelledDaypartCanBeCancelledAndBookedAgainManyTimes() = withApp {
        val jan = client.signUp("jan@example.com")
        repeat(3) {
            val id = repository.reserve(car, jan.id, tomorrow, Daypart.EVENING)!!.id
            assertEquals(CancelResult.CANCELLED, repository.cancel(id))
        }
        assertEquals(3, client.list("?status=CANCELLED", jan.token).reservations().size)
    }

    @Test
    fun ACancelledReservationCannotBeCancelledAgain() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = repository.reserve(car, jan.id, tomorrow, Daypart.MORNING)!!
        assertEquals(HttpStatusCode.NoContent, client.cancel(reservation.id, jan.token).status)

        assertEquals(HttpStatusCode.Conflict, client.cancel(reservation.id, jan.token).status)
    }

    @Test
    fun ACompletedReservationCannotBeCancelled() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = repository.reserve(car, jan.id, tomorrow, Daypart.MORNING)!!
        TripRepository(TestDatabase.dataSource).record(reservation.id, jan.id, 10, 100, 10)

        assertEquals(HttpStatusCode.Conflict, client.cancel(reservation.id, jan.token).status)
        assertEquals(ReservationStatus.COMPLETED, repository.find(reservation.id)!!.status)
    }

    @Test
    fun RecordingATripCompletesTheReservationAndEndingAStartedTripToo() = withApp {
        val jan = client.signUp("jan@example.com")
        val oneCall = repository.reserve(car, jan.id, today.minusDays(1), Daypart.MORNING)!!
        val started = repository.reserve(car, jan.id, today, Daypart.EVENING)!!

        client.post("/reservations/${oneCall.id}/trip") {
            bearerAuth(jan.token); contentType(ContentType.Application.Json); setBody(trip())
        }
        client.post("/reservations/${started.id}/trip/start") { bearerAuth(jan.token) }
        assertEquals(ReservationStatus.RESERVED, repository.find(started.id)!!.status) // started is not finished
        client.post("/reservations/${started.id}/trip/end") {
            bearerAuth(jan.token); contentType(ContentType.Application.Json); setBody(trip())
        }

        assertEquals(ReservationStatus.COMPLETED, repository.find(oneCall.id)!!.status)
        assertEquals(ReservationStatus.COMPLETED, repository.find(started.id)!!.status)
    }

    @Test
    fun ACancelledReservationGets409ForEveryTripCallAndNoPoints() = withApp {
        val jan = client.signUp("jan@example.com")
        val past = repository.reserve(car, jan.id, today.minusDays(1), Daypart.MORNING)!!
        val now = repository.reserve(car, jan.id, today, Daypart.EVENING)!!
        repository.cancel(past.id)
        repository.cancel(now.id)

        for (path in listOf("/reservations/${past.id}/trip", "/reservations/${now.id}/trip/start", "/reservations/${now.id}/trip/end")) {
            val response = client.post(path) {
                bearerAuth(jan.token); contentType(ContentType.Application.Json); setBody(trip())
            }
            assertEquals(HttpStatusCode.Conflict, response.status, path)
        }
        assertEquals(ReservationStatus.CANCELLED, repository.find(now.id)!!.status)
    }

    @Test
    fun ACancelledReservationLeavesTheAvailabilityAndTheFreeCarSearchAlone() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = repository.reserve(car, jan.id, tomorrow, Daypart.MORNING)!!
        assertTrue(car in repository.reservedCarIds(tomorrow, Daypart.MORNING))

        repository.cancel(reservation.id)

        assertFalse(car in repository.reservedCarIds(tomorrow, Daypart.MORNING))
        val free = Json.parseToJsonElement(client.get("/cars/$car/availability?date=$tomorrow").bodyAsText()).jsonObject["freeDayparts"]!!.jsonArray
        assertEquals(3, free.size)
    }

    @Test
    fun ATableWithoutStatusIsUpgradedAndKeepsItsReservationsAndTheirTrips() {
        val users = UserRepository(TestDatabase.dataSource)
        val jan = users.register("Jan", "upgrade@example.com", "supersecret")!!.id
        val withTrip = repository.reserve(car, jan, today.minusDays(2), Daypart.MORNING)!!
        val without = repository.reserve(car, jan, tomorrow, Daypart.MORNING)!!
        TripRepository(TestDatabase.dataSource).record(withTrip.id, jan, 10, 100, 10)
        TestDatabase.dataSource.connection.use {
            it.createStatement().execute("DROP INDEX reservations_active_slot_key")
            it.createStatement().execute("ALTER TABLE reservations DROP COLUMN status")
            it.createStatement().execute("CREATE UNIQUE INDEX reservations_car_id_date_daypart_key ON reservations (car_id, date, daypart)")
        }

        val upgraded = ReservationRepository(TestDatabase.dataSource)
        TripRepository(TestDatabase.dataSource) // runs the backfill of the completed ones

        assertEquals(ReservationStatus.COMPLETED, upgraded.find(withTrip.id)!!.status)
        assertEquals(ReservationStatus.RESERVED, upgraded.find(without.id)!!.status)
        // the old unique index is gone: a cancelled daypart can be booked again
        assertEquals(CancelResult.CANCELLED, upgraded.cancel(without.id))
        assertNotNull(upgraded.reserve(car, jan, tomorrow, Daypart.MORNING))
        assertNull(upgraded.reserve(car, jan, tomorrow, Daypart.MORNING)) // but an active one still blocks
    }
}
