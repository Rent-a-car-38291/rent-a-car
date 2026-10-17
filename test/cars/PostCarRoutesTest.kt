package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.configureSerialization
import com.example.organizations.OrganizationRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class PostCarRoutesTest {
    private fun carRequest(
        type: String,
        licensePlate: String,
        extraFields: String = "",
    ) = """{
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

    private suspend fun HttpClient.signUp(): Pair<Int, String> {
        val registration = post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"jan@example.com","password":"supersecret"}""")
        }
        val userId = Json.parseToJsonElement(registration.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.int
        val response = post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"jan@example.com","password":"supersecret"}""")
        }
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return userId to json["token"]!!.jsonPrimitive.content
    }

    @Test
    fun PostCarsCreatesAllSupportedTypesForAuthenticatedUser() = testApplication {
        useTestDatabase()
        UserRepository(TestDatabase.dataSource).clear()
        val repository = InMemoryCarRepository()
        application {
            configureSerialization()
            val users = UserRepository(TestDatabase.dataSource)
            configureSecurity(users)
            configureAccounts(users, OrganizationRepository(TestDatabase.dataSource), repository)
            configureCars(repository)
        }
        val (userId, token) = client.signUp()
        val requests = listOf(
            carRequest("BEV", "EV-100", ""","capacity":75.0,"consumption":16.2"""),
            carRequest("ICE", "IC-200", ""","fuelType":"Petrol","capacity":55.0,"consumption":6.5"""),
            carRequest("FCEV", "FC-300", ""","capacity":6.0,"consumption":0.8"""),
        )

        requests.zip(listOf("BEV", "ICE", "FCEV")).forEach { (request, type) ->
            val response = client.post("/cars") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            assertEquals(HttpStatusCode.Created, response.status)
            val created = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals(type, created["type"]!!.jsonPrimitive.content)
            assertEquals(userId, created["ownerId"]!!.jsonPrimitive.content.toInt())
            assertEquals(CarStatus.Available.name, created["status"]!!.jsonPrimitive.content)
        }

        assertEquals(listOf("EV-100", "IC-200", "FC-300"), repository.cars.map { it.licensePlate })
        assertTrue(repository.cars.all { it.ownerId == userId })
        val bev = assertIs<BEVCar>(repository.cars[0])
        assertEquals(75.0, bev.batteryCapacityKwh)
        assertEquals(16.2, bev.consumptionKwhPer100Km)
        val ice = assertIs<ICECar>(repository.cars[1])
        assertEquals(FuelType.Petrol, ice.fuelType)
        assertEquals(55.0, ice.tankCapacityLitres)
        val fcev = assertIs<FCEVCar>(repository.cars[2])
        assertEquals(6.0, fcev.tankCapacityKg)
        assertEquals(0.8, fcev.consumptionKgPer100Km)
    }

    @Test
    fun PostCarsWithoutAuthenticationReturnsUnauthorized() = testApplication {
        useTestDatabase()
        UserRepository(TestDatabase.dataSource).clear()
        val repository = InMemoryCarRepository()
        application {
            configureSerialization()
            val users = UserRepository(TestDatabase.dataSource)
            configureSecurity(users)
            configureAccounts(users, OrganizationRepository(TestDatabase.dataSource), repository)
            configureCars(repository)
        }

        val response = client.post("/cars") {
            contentType(ContentType.Application.Json)
            setBody(carRequest("BEV", "EV-100", ""","capacity":75.0,"consumption":16.2"""))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(repository.cars.isEmpty())
    }
}
