package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.configureSerialization
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class DeleteCarByIdRouteTest {

    private suspend fun HttpClient.signUpAndGetToken(email: String = "test@email.com"): String {
        post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Test User","email":"$email","password":"supersecret"}""")
        }
        val response = post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"supersecret"}""")
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
            configureAccounts(users, OrganizationRepository(TestDatabase.dataSource))
            configureCars(repository)
        }
    }

    private fun carRequest(type: String, licensePlate: String, extraFields: String = "") = """{
        "type":"$type",
        "brand":"Test Motors",
        "licensePlate":"$licensePlate",
        "mileage":1200,
        "rate":45.50,
        "location":{"latitude":52.3676,"longitude":4.9041},
        "doors":4,
        "bodyStyle":"Sedan",
        "modelYear":2024,
        "power":150,
        "transmission":"Automatic",
        "color":"Blue"
        $extraFields
    }"""

    @Test
    fun DeleteCarSucceedsAndSubsequentGetReturns404() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)

        val token = client.signUpAndGetToken()

        val createResponse = client.post("/cars") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(carRequest("BEV", "DEL-TEST-1", ""","capacity":75.0,"consumption":16.2"""))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val createdJson = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val carId = createdJson["id"]!!.jsonPrimitive.int

        val getBeforeResponse = client.get("/cars/$carId") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, getBeforeResponse.status)

        val deleteResponse = client.delete("/cars/$carId") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val getAfterResponse = client.get("/cars/$carId") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.NotFound, getAfterResponse.status)

        assertNull(repository.findCar(carId))
    }

    @Test
    fun DeleteCarWithUnknownIdReturns404() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)

        val token = client.signUpAndGetToken()
        val unknownId = 99999

        val deleteResponse = client.delete("/cars/$unknownId") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NotFound, deleteResponse.status)

        assertTrue(repository.cars.isEmpty())
    }

    @Test
    fun DeleteCarWithoutTokenReturns401() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)

        val deleteResponse = client.delete("/cars/1") {
        }

        assertEquals(HttpStatusCode.Unauthorized, deleteResponse.status)
        assertTrue(repository.cars.isEmpty())
    }

    @Test
    fun DeleteCarOwnedByAnotherUserReturns404() = testApplication {
        val repository = InMemoryCarRepository()
        setupApp(repository)

        val token1 = client.signUpAndGetToken()
        val createResponse = client.post("/cars") {
            bearerAuth(token1)
            contentType(ContentType.Application.Json)
            setBody(carRequest("BEV", "NOT-YOURS-1", ""","capacity":75.0,"consumption":16.2"""))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val createdJson = Json.parseToJsonElement(createResponse.bodyAsText()).jsonObject
        val carId = createdJson["id"]!!.jsonPrimitive.int

        val token2 = client.signUpAndGetToken("user2@example.com")

        val deleteResponse = client.delete("/cars/$carId") {
            bearerAuth(token2)
        }

        assertEquals(HttpStatusCode.Forbidden, deleteResponse.status)

        assertNotNull(repository.findCar(carId))
    }
}