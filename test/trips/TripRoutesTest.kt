package com.example.trips

import com.example.accounts.UserRepository
import com.example.cars.CarStatus
import com.example.cars.PostgresCarRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import kotlin.test.*

class TripRoutesTest {
    private val car = PostgresCarRepository(TestDatabase.dataSource).allCars().first { it.status == CarStatus.Available }.id

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

    // Reserves a daypart of the car (a new date each time) and returns the reservation id.
    private var day = 0
    private suspend fun HttpClient.reserve(renter: Renter): Int {
        val response = post("/cars/$car/reservations") {
            bearerAuth(renter.token)
            contentType(ContentType.Application.Json)
            setBody("""{"date":"2031-01-${"%02d".format(++day)}","daypart":"MORNING"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
    }

    private suspend fun HttpClient.trip(reservationId: Any, body: String, token: String? = null): HttpResponse =
        post("/reservations/$reservationId/trip") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
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
    fun ARecordedTripGives201WithTheTripAndTheNewBonusPoints() = withApp {
        val jan = client.signUp("jan@example.com")
        assertEquals(0, client.bonusPoints(jan))
        val reservation = client.reserve(jan)

        val response = client.trip(reservation, trip(100, accelerations = 2, brakes = 3), jan.token)

        assertEquals(HttpStatusCode.Created, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val trip = json["trip"]!!.jsonObject
        assertEquals(reservation, trip["reservationId"]!!.jsonPrimitive.int)
        assertEquals(100, trip["distanceKm"]!!.jsonPrimitive.int)
        assertEquals(75, trip["score"]!!.jsonPrimitive.int)
        assertEquals(75, json["pointsAwarded"]!!.jsonPrimitive.int)
        assertEquals(75, json["bonusPoints"]!!.jsonPrimitive.int)
        assertEquals(75, client.bonusPoints(jan))
    }

    @Test
    fun PointsAddUpOverTrips() = withApp {
        val jan = client.signUp("jan@example.com")
        client.trip(client.reserve(jan), trip(100, accelerations = 2, brakes = 3), jan.token)

        val second = client.trip(client.reserve(jan), trip(10), jan.token)

        assertEquals(85, Json.parseToJsonElement(second.bodyAsText()).jsonObject["bonusPoints"]!!.jsonPrimitive.int)
    }

    @Test
    fun ASecondTripForTheSameReservationGives409AndNoExtraPoints() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = client.reserve(jan)
        assertEquals(HttpStatusCode.Created, client.trip(reservation, trip(10), jan.token).status)

        assertEquals(HttpStatusCode.Conflict, client.trip(reservation, trip(10), jan.token).status)
        assertEquals(10, client.bonusPoints(jan))
    }

    @Test
    fun InvalidNumbersGive400AndNoTrip() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = client.reserve(jan)

        assertEquals(HttpStatusCode.BadRequest, client.trip(reservation, trip(-1), jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.trip(reservation, trip(10, accelerations = -1), jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.trip(reservation, trip(10, brakes = -1), jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.trip(reservation, trip(MAX_TRIP_KM + 1), jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.trip(reservation, """{"distanceKm":10,"hardAccelerations":0}""", jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.trip(reservation, """{"distanceKm":"far","hardAccelerations":0,"hardBrakes":0}""", jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.trip("abc", trip(10), jan.token).status)
        assertEquals(0, client.bonusPoints(jan))
        assertEquals(HttpStatusCode.Created, client.trip(reservation, trip(10), jan.token).status)
    }

    @Test
    fun WithoutATokenGives401() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.Unauthorized, client.trip(client.reserve(jan), trip(10)).status)
    }

    @Test
    fun SomeoneElsesReservationGives403() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val reservation = client.reserve(jan)

        assertEquals(HttpStatusCode.Forbidden, client.trip(reservation, trip(10), piet.token).status)
        assertEquals(HttpStatusCode.Created, client.trip(reservation, trip(10), jan.token).status)
    }

    @Test
    fun AnUnknownReservationGives404() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.NotFound, client.trip(-42, trip(10), jan.token).status)
    }

    @Test
    fun TwoTripsAtOnceGiveExactlyOne201AndOnePayout() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = client.reserve(jan)

        val statuses = coroutineScope { (1..8).map { async { client.trip(reservation, trip(10), jan.token).status } }.awaitAll() }

        assertEquals(1, statuses.count { it == HttpStatusCode.Created })
        assertEquals(7, statuses.count { it == HttpStatusCode.Conflict })
        assertEquals(10, client.bonusPoints(jan))
    }

    @Test
    fun DeletingTheRenterWithATripWorks() = withApp {
        val jan = client.signUp("jan@example.com")
        client.trip(client.reserve(jan), trip(10), jan.token)

        val response = client.delete("/accounts/${jan.id}") { bearerAuth(jan.token) }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }
}
