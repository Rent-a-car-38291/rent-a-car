package com.example.trips

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import com.example.reservations.ZONE
import com.example.reservations.Daypart
import com.example.reservations.ZONE
import com.example.reservations.ReservationRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
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

class DrivingStatsRoutesTest {
    private val reservations = ReservationRepository(TestDatabase.dataSource)
    private val trips = TripRepository(TestDatabase.dataSource)

    @BeforeTest
    fun reset() {
        OrganizationRepository(TestDatabase.dataSource).clear() // also removes the organization cars
        UserRepository(TestDatabase.dataSource).clear() // and with the users their cars, reservations and trips
    }

    private class User(val id: Int, val token: String)

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        block()
    }

    private suspend fun HttpClient.postJson(path: String, body: String, token: String? = null) =
        post(path) {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.json() = Json.parseToJsonElement(bodyAsText())

    private suspend fun HttpClient.signUp(email: String): User {
        val id = postJson("/accounts", """{"name":"Jan","email":"$email","password":"supersecret"}""").json().jsonObject["id"]!!.jsonPrimitive.int
        return User(id, postJson("/login", """{"email":"$email","password":"supersecret"}""").json().jsonObject["token"]!!.jsonPrimitive.content)
    }

    private fun carRequest(licensePlate: String) = """{
        "type":"BEV","brand":"Test Motors","licensePlate":"$licensePlate","mileage":1,"rate":45.5,
        "location":{"latitude":52.3,"longitude":4.9},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue","capacity":75.0,"consumption":16.2}"""

    private suspend fun HttpClient.addCar(owner: User, licensePlate: String): Int {
        val response = postJson("/cars", carRequest(licensePlate), owner.token)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.json().jsonObject["id"]!!.jsonPrimitive.int
    }

    // The API only takes a trip for a reservation that has started, but a test needs trips on chosen dates, so it saves them directly.
    private fun trip(carId: Int, user: User, date: String, daypart: Daypart, km: Int = 10, score: Int = 100): Int {
        val reservation = reservations.reserve(carId, user.id, LocalDate.parse(date), daypart)!!
        trips.record(reservation.id, user.id, km, score, 0)
        return reservation.id
    }

    private suspend fun HttpClient.stats(path: String, token: String?) = get(path) { token?.let { bearerAuth(it) } }

    @Test
    fun ARenterWithTripsSeesTheirTotals() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "STAT-1")
        trip(car, jan, "2026-01-01", Daypart.MORNING, km = 10, score = 100)
        trip(car, jan, "2026-01-02", Daypart.MORNING, km = 30, score = 60)
        trip(car, jan, "2026-01-03", Daypart.EVENING, km = 60, score = 90)

        val response = client.stats("/accounts/${jan.id}/stats", jan.token)

        assertEquals(HttpStatusCode.OK, response.status)
        val stats = response.json().jsonObject
        assertEquals(3, stats["tripCount"]!!.jsonPrimitive.int)
        assertEquals(100, stats["totalKm"]!!.jsonPrimitive.int)
        assertEquals(83.3, stats["averageScore"]!!.jsonPrimitive.double)
        assertEquals("EVENING", stats["bestDaypart"]!!.jsonPrimitive.content)
        assertEquals(0, stats["bonusPoints"]!!.jsonPrimitive.int)
    }

    @Test
    fun ARenterWithoutTripsGetsZerosAndNulls() = withApp {
        val jan = client.signUp("jan@example.com")

        val stats = client.stats("/accounts/${jan.id}/stats", jan.token).json().jsonObject

        assertEquals(0, stats["tripCount"]!!.jsonPrimitive.int)
        assertEquals(0, stats["totalKm"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, stats["averageScore"])
        assertEquals(JsonNull, stats["bestDaypart"])
    }

    @Test
    fun TheTotalsCoverOnlyTheTripsOfThatUser() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val car = client.addCar(jan, "STAT-2")
        trip(car, jan, "2026-01-01", Daypart.MORNING, km = 10)
        trip(car, piet, "2026-01-02", Daypart.AFTERNOON, km = 500)

        assertEquals(10, client.stats("/accounts/${jan.id}/stats", jan.token).json().jsonObject["totalKm"]!!.jsonPrimitive.int)
        assertEquals(500, client.stats("/accounts/${piet.id}/stats", piet.token).json().jsonObject["totalKm"]!!.jsonPrimitive.int)
    }

    @Test
    fun ABonusPointsTotalFollowsTheTripsRecordedThroughTheApi() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "STAT-3")
        val reservation = reservations.reserve(car, jan.id, LocalDate.now(ZONE), Daypart.MORNING)!!
        assertEquals(HttpStatusCode.Created, client.postJson("/reservations/${reservation.id}/trip", """{"distanceKm":40,"hardAccelerations":0,"hardBrakes":0}""", jan.token).status)

        val stats = client.stats("/accounts/${jan.id}/stats", jan.token).json().jsonObject

        assertEquals(40, stats["bonusPoints"]!!.jsonPrimitive.int)
        assertEquals(1, stats["tripCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun AStartedTripThatHasNotEndedDoesNotCount() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "STAT-4")
        val reservation = reservations.reserve(car, jan.id, LocalDate.now(ZONE), Daypart.MORNING)!!
        assertEquals(HttpStatusCode.OK, client.postJson("/reservations/${reservation.id}/trip/start", "{}", jan.token).status)

        val stats = client.stats("/accounts/${jan.id}/stats", jan.token).json().jsonObject
        assertEquals(0, stats["tripCount"]!!.jsonPrimitive.int)
        assertEquals(0, stats["totalKm"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, stats["averageScore"])
    }

    @Test
    fun WithoutATokenTheAnswerIs401() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.Unauthorized, client.stats("/accounts/${jan.id}/stats", null).status)
    }

    @Test
    fun SomeoneElsesStatsAre403() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")

        assertEquals(HttpStatusCode.Forbidden, client.stats("/accounts/${piet.id}/stats", jan.token).status)
    }

    @Test
    fun AnUnknownAccountIs404AndANonNumericIdIs400() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.NotFound, client.stats("/accounts/999999/stats", jan.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.stats("/accounts/abc/stats", jan.token).status)
    }
}
