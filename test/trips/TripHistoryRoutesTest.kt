package com.example.trips

import com.example.MAX_LIMIT
import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import com.example.reservations.Daypart
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

class TripHistoryRoutesTest {
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

    private fun carRequest(licensePlate: String, organizationId: Int? = null) = """{
        "type":"BEV","brand":"Test Motors","licensePlate":"$licensePlate","mileage":1,"rate":45.5,
        "location":{"latitude":52.3,"longitude":4.9},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue","capacity":75.0,"consumption":16.2
        ${organizationId?.let { ""","organizationId":$it""" } ?: ""}}"""

    private suspend fun HttpClient.addCar(owner: User, licensePlate: String, organizationId: Int? = null): Int {
        val response = postJson("/cars", carRequest(licensePlate, organizationId), owner.token)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.json().jsonObject["id"]!!.jsonPrimitive.int
    }

    // The API only takes a trip for a reservation that has started, but a test needs trips on chosen dates, so it saves them directly.
    private fun trip(carId: Int, user: User, date: String, daypart: Daypart, km: Int = 10, score: Int = 100): Int {
        val reservation = reservations.reserve(carId, user.id, LocalDate.parse(date), daypart)!!
        trips.record(reservation.id, user.id, km, score, 0)
        return reservation.id
    }

    private suspend fun HttpClient.history(path: String, token: String?) = get(path) { token?.let { bearerAuth(it) } }

    private suspend fun HttpResponse.reservationIds() = json().jsonArray.map { it.jsonObject["reservationId"]!!.jsonPrimitive.int }

    @Test
    fun TripsComeNewestFirstWithTheirCarDateAndDaypart() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "HIS-1")
        val old = trip(car, jan, "2026-01-01", Daypart.EVENING)
        val morning = trip(car, jan, "2026-02-01", Daypart.MORNING, km = 20, score = 80)
        val evening = trip(car, jan, "2026-02-01", Daypart.EVENING)
        val afternoon = trip(car, jan, "2026-02-01", Daypart.AFTERNOON)

        val response = client.history("/trips", jan.token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(evening, afternoon, morning, old), response.reservationIds())
        val item = response.json().jsonArray[2].jsonObject
        assertEquals(car, item["carId"]!!.jsonPrimitive.int)
        assertEquals("2026-02-01", item["date"]!!.jsonPrimitive.content)
        assertEquals("MORNING", item["daypart"]!!.jsonPrimitive.content)
        assertEquals(20, item["distanceKm"]!!.jsonPrimitive.int)
        assertEquals(80, item["score"]!!.jsonPrimitive.int)
    }

    @Test
    fun ARenterOnlySeesTheirOwnTrips() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val car = client.addCar(jan, "HIS-2")
        val janTrip = trip(car, jan, "2026-01-01", Daypart.MORNING)
        val pietTrip = trip(car, piet, "2026-01-02", Daypart.MORNING)

        assertEquals(listOf(janTrip), client.history("/trips", jan.token).reservationIds())
        assertEquals(listOf(pietTrip), client.history("/trips", piet.token).reservationIds())
        assertEquals(emptyList(), client.history("/trips", client.signUp("kees@example.com").token).reservationIds())
    }

    @Test
    fun FromAndToLimitTheDatesInclusively() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "HIS-3")
        trip(car, jan, "2026-01-01", Daypart.MORNING)
        val middle = trip(car, jan, "2026-02-01", Daypart.MORNING)
        val last = trip(car, jan, "2026-03-01", Daypart.MORNING)

        assertEquals(listOf(last, middle), client.history("/trips?from=2026-02-01", jan.token).reservationIds())
        assertEquals(listOf(middle), client.history("/trips?from=2026-02-01&to=2026-02-01", jan.token).reservationIds())
        assertEquals(3, client.history("/trips?to=2026-03-01", jan.token).reservationIds().size)
    }

    @Test
    fun LimitAndOffsetPageThroughTheTrips() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "HIS-4")
        val ids = listOf("2026-01-01", "2026-01-02", "2026-01-03", "2026-01-04", "2026-01-05").map { trip(car, jan, it, Daypart.MORNING) }.reversed()

        assertEquals(ids.take(2), client.history("/trips?limit=2", jan.token).reservationIds())
        assertEquals(ids.drop(2).take(2), client.history("/trips?limit=2&offset=2", jan.token).reservationIds())
        assertEquals(ids.drop(4), client.history("/trips?limit=2&offset=4", jan.token).reservationIds())
        assertEquals(emptyList(), client.history("/trips?offset=10", jan.token).reservationIds())
        assertEquals("5", client.history("/trips?limit=2", jan.token).headers["X-Total-Count"])
        assertEquals("5", client.history("/cars/$car/trips?limit=2", jan.token).headers["X-Total-Count"])
        assertEquals("2", client.history("/trips?from=2026-01-04", jan.token).headers["X-Total-Count"]) // the total follows the filters
    }

    @Test
    fun InvalidValuesGive400AndNoTokenGives401() = withApp {
        val jan = client.signUp("jan@example.com")

        listOf("from=yesterday", "to=2026-13-01", "limit=0", "limit=${MAX_LIMIT + 1}", "limit=x", "offset=-1", "from=2026-02-02&to=2026-02-01").forEach {
            assertEquals(HttpStatusCode.BadRequest, client.history("/trips?$it", jan.token).status, it)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.history("/trips", null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.history("/trips", "not-a-token").status)
    }

    @Test
    fun TheOwnerSeesTheTripsOfEveryRenterOfTheirCar() = withApp {
        val owner = client.signUp("owner@example.com")
        val renter = client.signUp("renter@example.com")
        val car = client.addCar(owner, "CAR-1")
        val otherCar = client.addCar(owner, "CAR-2")
        val first = trip(car, renter, "2026-01-01", Daypart.MORNING)
        val second = trip(car, owner, "2026-01-02", Daypart.MORNING)
        trip(otherCar, renter, "2026-01-03", Daypart.MORNING)

        val response = client.history("/cars/$car/trips", owner.token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf(second, first), response.reservationIds())
        assertEquals(listOf(first), client.history("/cars/$car/trips?to=2026-01-01&limit=1", owner.token).reservationIds())
    }

    @Test
    fun AnOrganizationCarShowsItsTripsToMembersOnly() = withApp {
        val admin = client.signUp("admin@example.com")
        val outsider = client.signUp("outsider@example.com")
        val organizationId = client.postJson("/organizations", """{"name":"Rent Co"}""", admin.token).json().jsonObject["id"]!!.jsonPrimitive.int
        val car = client.addCar(admin, "ORG-1", organizationId)
        val renterTrip = trip(car, outsider, "2026-01-01", Daypart.MORNING)

        assertEquals(listOf(renterTrip), client.history("/cars/$car/trips", admin.token).reservationIds())
        assertEquals(HttpStatusCode.Forbidden, client.history("/cars/$car/trips", outsider.token).status)
    }

    @Test
    fun CarTripsAnswer401403404And400() = withApp {
        val owner = client.signUp("owner@example.com")
        val other = client.signUp("other@example.com")
        val car = client.addCar(owner, "CAR-3")

        assertEquals(HttpStatusCode.Unauthorized, client.history("/cars/$car/trips", null).status)
        assertEquals(HttpStatusCode.Forbidden, client.history("/cars/$car/trips", other.token).status)
        assertEquals(HttpStatusCode.NotFound, client.history("/cars/999999/trips", owner.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.history("/cars/abc/trips", owner.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.history("/cars/$car/trips?limit=0", owner.token).status)
        assertEquals(HttpStatusCode.BadRequest, client.history("/cars/$car/trips?from=2026-02-02&to=2026-02-01", owner.token).status)
        assertEquals(HttpStatusCode.OK, client.history("/cars/$car/trips", owner.token).status)
    }
}
