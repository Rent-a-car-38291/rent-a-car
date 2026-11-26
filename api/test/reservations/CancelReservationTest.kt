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
import java.time.LocalDateTime
import kotlin.test.*

class CancelReservationTest {
    private val repository = ReservationRepository(TestDatabase.dataSource)
    private val car = PostgresCarRepository(TestDatabase.dataSource).allCars().first { it.status == CarStatus.Available }.id
    private val tomorrow = LocalDate.now(ZONE).plusDays(1)
    private val yesterday = LocalDate.now(ZONE).minusDays(1)

    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    // Registers the users and passes their ids and tokens, in the order of the emails.
    private fun withApp(vararg emails: String, block: suspend ApplicationTestBuilder.(users: List<Pair<Int, String>>) -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        val users = emails.map { email ->
            val register = client.post("/accounts") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Jan","email":"$email","password":"supersecret"}""")
            }
            val id = Json.parseToJsonElement(register.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
            val login = client.post("/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"$email","password":"supersecret"}""")
            }
            id to Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        }
        block(users)
    }

    private suspend fun HttpClient.cancel(id: String, token: String? = null): HttpResponse = delete("/reservations/$id") { token?.let { bearerAuth(it) } }

    private suspend fun HttpClient.reserve(date: LocalDate, daypart: String, token: String) = post("/cars/$car/reservations") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody("""{"date":"$date","daypart":"$daypart"}""")
    }

    @Test
    fun CancellingAComingReservationGives204AndFreesTheDaypartForSomeoneElse() = withApp("jan@example.com", "piet@example.com") { (jan, piet) ->
        val reservation = repository.reserve(car, jan.first, tomorrow, Daypart.MORNING)!!

        assertEquals(HttpStatusCode.NoContent, client.cancel(reservation.id.toString(), jan.second).status)

        assertEquals(ReservationStatus.CANCELLED, repository.find(reservation.id)!!.status) // kept, not deleted
        assertEquals(HttpStatusCode.Created, client.reserve(tomorrow, "MORNING", piet.second).status)
    }

    @Test
    fun CancellingTwiceGives409TheSecondTime() = withApp("jan@example.com") { (jan) ->
        val reservation = repository.reserve(car, jan.first, tomorrow, Daypart.AFTERNOON)!!
        assertEquals(HttpStatusCode.NoContent, client.cancel(reservation.id.toString(), jan.second).status)

        assertEquals(HttpStatusCode.Conflict, client.cancel(reservation.id.toString(), jan.second).status)
    }

    @Test
    fun WithoutATokenTheAnswerIs401() = withApp("jan@example.com") { (jan) ->
        val reservation = repository.reserve(car, jan.first, tomorrow, Daypart.EVENING)!!

        assertEquals(HttpStatusCode.Unauthorized, client.cancel(reservation.id.toString()).status)
        assertNotNull(repository.find(reservation.id))
    }

    @Test
    fun SomeoneElsesReservationGives403AndStays() = withApp("jan@example.com", "piet@example.com") { (jan, piet) ->
        val reservation = repository.reserve(car, jan.first, tomorrow, Daypart.EVENING)!!

        assertEquals(HttpStatusCode.Forbidden, client.cancel(reservation.id.toString(), piet.second).status)
        assertNotNull(repository.find(reservation.id))
    }

    @Test
    fun AnUnknownReservationGives404AndANonNumericIdGives400() = withApp("jan@example.com") { (jan) ->
        assertEquals(HttpStatusCode.NotFound, client.cancel("999999", jan.second).status)
        assertEquals(HttpStatusCode.BadRequest, client.cancel("abc", jan.second).status)
    }

    @Test
    fun AReservationWhoseDaypartHasStartedGives409AndStays() = withApp("jan@example.com") { (jan) ->
        val reservation = repository.reserve(car, jan.first, yesterday, Daypart.EVENING)!!

        assertEquals(HttpStatusCode.Conflict, client.cancel(reservation.id.toString(), jan.second).status)
        assertNotNull(repository.find(reservation.id))
    }

    @Test
    fun AReservationWithATripIsNotCancelledAndItsTripStays() = withApp("jan@example.com") { (jan) ->
        val trips = TripRepository(TestDatabase.dataSource)
        val reservation = repository.reserve(car, jan.first, tomorrow, Daypart.MORNING)!!
        assertNotNull(trips.record(reservation.id, jan.first, 10, 100, 10))

        assertEquals(CancelResult.NOT_ALLOWED, repository.cancel(reservation.id))

        assertEquals(ReservationStatus.COMPLETED, repository.find(reservation.id)!!.status)
        assertNull(trips.record(reservation.id, jan.first, 10, 100, 10)) // still there: a second trip is refused
    }

    @Test
    fun TheRouteAnswers409ForAComingReservationThatHasATrip() = withApp("jan@example.com") { (jan) ->
        val reservation = repository.reserve(car, jan.first, tomorrow, Daypart.MORNING)!!
        TripRepository(TestDatabase.dataSource).record(reservation.id, jan.first, 10, 100, 10)

        assertEquals(HttpStatusCode.Conflict, client.cancel(reservation.id.toString(), jan.second).status)
        assertNotNull(repository.find(reservation.id))
    }

    @Test
    fun CancellingAGoneReservationIsReported() = withApp("jan@example.com") {
        assertEquals(CancelResult.GONE, repository.cancel(999_999))
    }

    @Test
    fun ADaypartHasStartedFromItsFromTimeOn() {
        val date = LocalDate.of(2030, 6, 15)
        Daypart.entries.forEach { daypart ->
            val from = date.atTime(daypart.from)
            assertFalse(hasStarted(date, daypart, from.minusNanos(1)), "$daypart just before")
            assertTrue(hasStarted(date, daypart, from), "$daypart at the start")
            assertTrue(hasStarted(date, daypart, from.plusMinutes(1)), "$daypart just after")
        }
    }

    @Test
    fun ADaypartOfAnotherDayStartsOnThatDay() {
        val now = LocalDateTime.of(2030, 6, 15, 12, 0)
        assertTrue(hasStarted(LocalDate.of(2030, 6, 14), Daypart.EVENING, now))
        assertFalse(hasStarted(LocalDate.of(2030, 6, 16), Daypart.MORNING, now))
        assertFalse(hasStarted(LocalDate.of(2030, 6, 15), Daypart.EVENING, now))
        assertTrue(hasStarted(LocalDate.of(2030, 6, 15), Daypart.MORNING, LocalDateTime.of(2030, 6, 15, 6, 0)))
    }
}
