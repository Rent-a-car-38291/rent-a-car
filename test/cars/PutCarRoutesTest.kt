package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.configureSerialization
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class PutCarRoutesTest {
    private val repository = InMemoryCarRepository()

    private fun carRequest(type: String = "BEV", rate: Double = 60.0, mileage: Int = 5000, extraFields: String = """"capacity":75.0,"consumption":16.2""") = """{
        "type":"$type","brand":"Test Motors","licensePlate":"EV-100","mileage":$mileage,"rate":$rate,
        "location":{"latitude":52.3676,"longitude":4.9041},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue",$extraFields
    }"""

    private suspend fun HttpClient.signUp(email: String): Pair<Int, String> {
        val registration = post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"$email","password":"supersecret"}""")
        }
        val userId = Json.parseToJsonElement(registration.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
        val login = post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"supersecret"}""")
        }
        return userId to Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private suspend fun HttpClient.putCar(id: Int, body: String, token: String? = null): HttpResponse =
        put("/cars/$id") {
            token?.let { bearerAuth(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    // Starts the application with an in-memory car repository and a car (id 1) owned by a new user; returns that user's token.
    private suspend fun ApplicationTestBuilder.withOwnedCar(): Pair<HttpClient, String> {
        useTestDatabase()
        UserRepository(TestDatabase.dataSource).clear()
        application {
            configureSerialization()
            val users = UserRepository(TestDatabase.dataSource)
            configureSecurity(users)
            configureAccounts(users)
            configureCars(repository)
        }
        val (ownerId, token) = client.signUp("jan@example.com")
        repository.cars.clear()
        repository.cars += AddCarRequest(
            CarType.BEV, "Old Motors", "OLD-1", 100, 40.0, Location(1.0, 2.0), 4, BodyStyle.Sedan, 2020, 100,
            Transmission.Manual, "Red", capacity = 50.0, consumption = 15.0,
        ).toCar(ownerId, 1).also { it.status = CarStatus.Rented }
        return client to token
    }

    @Test
    fun PutUpdatesTheCarAndKeepsOwnerAndStatus() = testApplication {
        val (client, token) = withOwnedCar()

        val response = client.putCar(1, carRequest(rate = 65.0, mileage = 6000), token)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(65.0, body["rate"]!!.jsonPrimitive.content.toDouble())
        assertEquals("EV-100", body["licensePlate"]!!.jsonPrimitive.content)
        assertEquals(repository.cars.single().ownerId, body["ownerId"]!!.jsonPrimitive.int)
        assertEquals(CarStatus.Rented.name, body["status"]!!.jsonPrimitive.content)
        assertEquals(6000, repository.cars.single().mileage)
    }

    @Test
    fun PutWithAnOwnerIdInTheBodyReturnsBadRequestAndKeepsTheOwner() = testApplication {
        val (client, token) = withOwnedCar()
        val ownerId = repository.cars.single().ownerId

        val response = client.putCar(1, carRequest(extraFields = """"ownerId":-1,"capacity":75.0,"consumption":16.2"""), token)

        assertEquals(HttpStatusCode.BadRequest, response.status) // unknown fields are rejected
        assertEquals(ownerId, repository.cars.single().ownerId)
    }

    @Test
    fun PutUnknownCarReturnsNotFound() = testApplication {
        val (client, token) = withOwnedCar()

        assertEquals(HttpStatusCode.NotFound, client.putCar(99, carRequest(), token).status)
    }

    @Test
    fun PutWithInvalidFieldReturnsBadRequestAndChangesNothing() = testApplication {
        val (client, token) = withOwnedCar()

        assertEquals(HttpStatusCode.BadRequest, client.putCar(1, carRequest(mileage = -1), token).status)
        assertEquals(HttpStatusCode.BadRequest, client.putCar(1, carRequest(rate = -5.0), token).status)
        assertEquals(HttpStatusCode.BadRequest, client.putCar(1, carRequest(rate = 1_000_000.0), token).status) // too big for the rate column
        assertEquals(HttpStatusCode.BadRequest, client.putCar(1, carRequest(extraFields = """"capacity":75.0"""), token).status)
        assertEquals("OLD-1", repository.cars.single().licensePlate)
    }

    @Test
    fun PutThatChangesTheTypeReturnsBadRequest() = testApplication {
        val (client, token) = withOwnedCar()

        val response = client.putCar(1, carRequest("ICE", extraFields = """"fuelType":"Petrol","capacity":55.0,"consumption":6.5"""), token)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertIs<BEVCar>(repository.cars.single())
    }

    @Test
    fun PutWithoutValidTokenReturnsUnauthorized() = testApplication {
        val (client, _) = withOwnedCar()

        assertEquals(HttpStatusCode.Unauthorized, client.putCar(1, carRequest()).status)
        assertEquals(HttpStatusCode.Unauthorized, client.putCar(1, carRequest(), "unknown").status)
    }

    @Test
    fun PutSomeoneElsesCarReturnsForbidden() = testApplication {
        val (client, _) = withOwnedCar()
        val (_, other) = client.signUp("piet@example.com")

        assertEquals(HttpStatusCode.Forbidden, client.putCar(1, carRequest(), other).status)
        assertEquals("OLD-1", repository.cars.single().licensePlate)
    }

    @Test
    fun PutWithALicensePlateOfAnotherCarReturnsConflict() = testApplication {
        val (client, token) = withOwnedCar()
        val first = repository.cars.single()
        repository.cars += AddCarRequest(
            CarType.BEV, "Other", "TAKEN-1", 100, 40.0, Location(1.0, 2.0), 4, BodyStyle.Sedan, 2020, 100,
            Transmission.Manual, "Red", capacity = 50.0, consumption = 15.0,
        ).toCar(first.ownerId, 2)

        val response = client.putCar(1, carRequest().replace("EV-100", "TAKEN-1"), token)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("OLD-1", repository.cars.first { it.id == 1 }.licensePlate)
    }

    @Test
    fun PutKeepingTheOwnLicensePlateIsFine() = testApplication {
        val (client, token) = withOwnedCar()

        assertEquals(HttpStatusCode.OK, client.putCar(1, carRequest().replace("EV-100", "OLD-1"), token).status)
    }
}
