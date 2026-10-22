package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.configureSerialization
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

class GetCarByIdRouteTest {

    private suspend fun HttpClient.signUpAndGetToken(): String {
        post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test User","email":"test@example.com","password":"supersecret"}""")
        }
        val response = post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"test@example.com","password":"supersecret"}""")
        }
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return json["token"]!!.jsonPrimitive.content
    }

    private fun ApplicationTestBuilder.setupApp(repository: CarRepository) {
        useTestDatabase()
        UserRepository(TestDatabase.dataSource).clear()
        application {
            configureSerialization()
            val users = UserRepository(TestDatabase.dataSource)
            configureSecurity(users)
            configureAccounts(users)
            configureCars(repository)
        }
    }

    @Test
    fun GetCarByIdReturns200OkForExistingCar() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)

        // Add a car to the in-memory list manually
        val testCar = BEVCar(
            id = 1,
            ownerId = 1,
            brand = "Test Brand",
            licensePlate = "TEST-001",
            mileage = 0,
            status = CarStatus.Available,
            rate = 50.0,
            location = Location(52.0, 4.0),
            doors = 4,
            bodyStyle = BodyStyle.Sedan,
            modelYear = 2024,
            power = 100,
            transmission = Transmission.Automatic,
            color = "White",
            batteryCapacityKwh = 60.0,
            consumptionKwhPer100Km = 15.0
        )
        repository.cars.add(testCar)

        val token = client.signUpAndGetToken()

        val response = client.get("/cars/1") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, json["id"]!!.jsonPrimitive.int)
        assertEquals("TEST-001", json["licensePlate"]!!.jsonPrimitive.content)
    }

    @Test
    fun GetCarByIdReturns400BadRequestForNonNumericId() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)
        val token = client.signUpAndGetToken()

        val response = client.get("/cars/abc") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("ID must be of numeric value", response.bodyAsText())
    }

    @Test
    fun GetCarByIdReturns404NotFoundForNonExistingCar() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)

        // Ensure no car with ID 999 exists
        val token = client.signUpAndGetToken()

        val response = client.get("/cars/999") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("Car with ID 999 not found", response.bodyAsText())
    }

    @Test
    fun GetCarByIdReturns401UnauthorizedWithoutAuthentication() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)

        val response = client.get("/cars/1") {
            // No bearerAuth provided
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}