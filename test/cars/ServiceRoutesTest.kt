package com.example.cars

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
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
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import kotlin.test.*

class ServiceRoutesTest {
    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        block()
    }

    private suspend fun HttpClient.signUp(email: String): String {
        post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"$email","password":"supersecret"}""")
        }
        val login = post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"supersecret"}""")
        }
        return Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private var plates = 0
    private fun carBody(mileage: Int, lastServiceKm: Int? = null) = """{
        "type":"BEV","brand":"Test Motors","licensePlate":"SV-${++plates}-${System.nanoTime()}","mileage":$mileage,"rate":60.0,
        "location":{"latitude":52.3676,"longitude":4.9041},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue","capacity":75.0,"consumption":16.2
        ${lastServiceKm?.let { ""","lastServiceKm":$it""" } ?: ""}
    }"""

    private suspend fun HttpClient.addCar(token: String, mileage: Int, lastServiceKm: Int? = null): HttpResponse =
        post("/cars") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(carBody(mileage, lastServiceKm))
        }

    private suspend fun HttpClient.addCarId(token: String, mileage: Int, lastServiceKm: Int? = null): Int =
        Json.parseToJsonElement(addCar(token, mileage, lastServiceKm).bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int

    private suspend fun HttpClient.car(token: String, id: Int): JsonObject =
        Json.parseToJsonElement(get("/cars/$id") { bearerAuth(token) }.bodyAsText()).jsonObject

    private suspend fun HttpClient.service(path: String, token: String? = null): HttpResponse =
        put(path) { token?.let { bearerAuth(it) } }

    @Test
    fun ANewCarIsServicedAtItsMileageUntilTrulyDriven() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.car(jan, client.addCarId(jan, mileage = 120_000))

        assertEquals(120_000, car["lastServiceKm"]!!.jsonPrimitive.int)
        assertFalse(car["serviceDue"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun ACarIsDueWhenMoreThan15000KmPassedSinceTheLastService() = withApp {
        val jan = client.signUp("jan@example.com")

        assertTrue(client.car(jan, client.addCarId(jan, 50_000, lastServiceKm = 34_999))["serviceDue"]!!.jsonPrimitive.boolean)
        assertFalse(client.car(jan, client.addCarId(jan, 50_000, lastServiceKm = 35_000))["serviceDue"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun PutServiceSetsTheLastServiceToTheMileageAndClearsDue() = withApp {
        val jan = client.signUp("jan@example.com")
        val id = client.addCarId(jan, 50_000, lastServiceKm = 10_000)
        assertTrue(client.car(jan, id)["serviceDue"]!!.jsonPrimitive.boolean)

        assertEquals(HttpStatusCode.NoContent, client.service("/cars/$id/service", jan).status)

        val car = client.car(jan, id)
        assertEquals(50_000, car["lastServiceKm"]!!.jsonPrimitive.int)
        assertFalse(car["serviceDue"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun PutServiceAnswers401ForNoTokenAnd403ForSomeoneElsesCarAnd404ForUnknownAnd400ForABadId() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val id = client.addCarId(jan, 50_000, lastServiceKm = 10_000)

        assertEquals(HttpStatusCode.Unauthorized, client.service("/cars/$id/service").status)
        assertEquals(HttpStatusCode.Forbidden, client.service("/cars/$id/service", piet).status)
        assertEquals(HttpStatusCode.NotFound, client.service("/cars/999999/service", jan).status)
        assertEquals(HttpStatusCode.BadRequest, client.service("/cars/abc/service", jan).status)
        assertTrue(client.car(jan, id)["serviceDue"]!!.jsonPrimitive.boolean) // nothing changed
    }

    @Test
    fun ANegativeOrTooHighLastServiceGives400() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.BadRequest, client.addCar(jan, 50_000, lastServiceKm = -1).status)
        assertEquals(HttpStatusCode.BadRequest, client.addCar(jan, 50_000, lastServiceKm = 50_001).status)
        assertEquals(HttpStatusCode.BadRequest, client.addCar(jan, -5).status)
    }

    @Test
    fun AnUpdateKeepsTheLastServiceWhenItIsLeftOut() = withApp {
        val jan = client.signUp("jan@example.com")
        val id = client.addCarId(jan, 50_000, lastServiceKm = 10_000)

        val updated = client.put("/cars/$id") {
            bearerAuth(jan)
            contentType(ContentType.Application.Json)
            setBody(carBody(55_000).replace(Regex("SV-\\d+-\\d+"), "SV-UPDATED-$id"))
        }

        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals(10_000, Json.parseToJsonElement(updated.bodyAsText()).jsonObject["lastServiceKm"]!!.jsonPrimitive.int)
    }

    @Test
    fun ARecordedTripRaisesTheMileageAndCanMakeTheCarDue() = withApp {
        val jan = client.signUp("jan@example.com")
        val id = client.addCarId(jan, 14_000, lastServiceKm = 0)
        assertFalse(client.car(jan, id)["serviceDue"]!!.jsonPrimitive.boolean)
        val reservation = client.post("/cars/$id/reservations") {
            bearerAuth(jan)
            contentType(ContentType.Application.Json)
            setBody("""{"date":"${LocalDate.now(com.example.reservations.ZONE)}","daypart":"MORNING"}""")
        }.let { Json.parseToJsonElement(it.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int }

        val trip = client.post("/reservations/$reservation/trip") {
            bearerAuth(jan)
            contentType(ContentType.Application.Json)
            setBody("""{"distanceKm":1001,"hardAccelerations":0,"hardBrakes":0}""")
        }

        assertEquals(HttpStatusCode.Created, trip.status)
        val car = client.car(jan, id)
        assertEquals(15_001, car["mileage"]!!.jsonPrimitive.int)
        assertTrue(car["serviceDue"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun ARejectedSecondTripDoesNotAddItsDistanceAgain() = withApp {
        val jan = client.signUp("jan@example.com")
        val id = client.addCarId(jan, 1_000)
        val reservation = client.post("/cars/$id/reservations") {
            bearerAuth(jan)
            contentType(ContentType.Application.Json)
            setBody("""{"date":"${LocalDate.now(com.example.reservations.ZONE)}","daypart":"MORNING"}""")
        }.let { Json.parseToJsonElement(it.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int }
        suspend fun trip() = client.post("/reservations/$reservation/trip") {
            bearerAuth(jan)
            contentType(ContentType.Application.Json)
            setBody("""{"distanceKm":100,"hardAccelerations":0,"hardBrakes":0}""")
        }

        assertEquals(HttpStatusCode.Created, trip().status)
        assertEquals(HttpStatusCode.Conflict, trip().status)

        assertEquals(1_100, client.car(jan, id)["mileage"]!!.jsonPrimitive.int)
    }

    @Test
    fun TheMileageStopsAtTheLimitSoTheCarCanStillBeUpdated() = withApp {
        val jan = client.signUp("jan@example.com")
        val id = client.addCarId(jan, MAX_MILEAGE - 5)
        val reservation = client.post("/cars/$id/reservations") {
            bearerAuth(jan)
            contentType(ContentType.Application.Json)
            setBody("""{"date":"${LocalDate.now(com.example.reservations.ZONE)}","daypart":"MORNING"}""")
        }.let { Json.parseToJsonElement(it.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int }

        val trip = client.post("/reservations/$reservation/trip") {
            bearerAuth(jan)
            contentType(ContentType.Application.Json)
            setBody("""{"distanceKm":100,"hardAccelerations":0,"hardBrakes":0}""")
        }

        assertEquals(HttpStatusCode.Created, trip.status)
        assertEquals(MAX_MILEAGE, client.car(jan, id)["mileage"]!!.jsonPrimitive.int)
    }
}
