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
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import kotlin.test.*

class TcoRouteTest {
    private val repository = InMemoryCarRepository()

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

    // Car 7 (BEV, 15 kWh/100 km = 0.0525 EUR/km) belongs to the first user: 10000 / 10 + 500 + 200 + 300 = 2000 fixed.
    private fun withApp(block: suspend ApplicationTestBuilder.(owner: String, other: String) -> Unit) = testApplication {
        useTestDatabase()
        UserRepository(TestDatabase.dataSource).clear()
        application {
            configureSerialization()
            val users = UserRepository(TestDatabase.dataSource)
            configureSecurity(users)
            configureAccounts(users, OrganizationRepository(TestDatabase.dataSource))
            configureCars(repository)
        }
        val (ownerId, owner) = client.signUp("owner@example.com")
        val (_, other) = client.signUp("other@example.com")
        repository.cars.clear()
        repository.cars += BEVCar(7, ownerId, "Tesla", "EV-1", 0, CarStatus.Available, 55.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2021, 208, Transmission.Automatic, "White", 60.0, 15.0, 10_000.0, 500.0, 200.0, 300.0)
        block(owner, other)
    }

    private suspend fun ApplicationTestBuilder.tco(path: String, token: String? = null): HttpResponse =
        client.get(path) { token?.let { bearerAuth(it) } }

    @Test
    fun OwnerGetsTheTcoForTheGivenKm() = withApp { owner, _ ->
        val response = tco("/cars/7/tco?kmPerYear=12000", owner)

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(7, json["carId"]!!.jsonPrimitive.int)
        assertEquals(12000, json["kmPerYear"]!!.jsonPrimitive.int)
        assertEquals(2630.0, json["tcoPerYear"]!!.jsonPrimitive.double, 1e-9) // 2000 + 0.0525 * 12000
        assertEquals("EUR", json["currency"]!!.jsonPrimitive.content)
    }

    @Test
    fun WithoutKmPerYearTheStandardIsUsed() = withApp { owner, _ ->
        val json = Json.parseToJsonElement(tco("/cars/7/tco", owner).bodyAsText()).jsonObject

        assertEquals(15000, json["kmPerYear"]!!.jsonPrimitive.int)
        assertEquals(2787.5, json["tcoPerYear"]!!.jsonPrimitive.double, 1e-9) // 2000 + 0.0525 * 15000
    }

    @Test
    fun WithoutTokenReturns401() = withApp { _, _ ->
        assertEquals(HttpStatusCode.Unauthorized, tco("/cars/7/tco").status)
    }

    @Test
    fun SomeoneElsesCarReturns403() = withApp { _, other ->
        assertEquals(HttpStatusCode.Forbidden, tco("/cars/7/tco", other).status)
    }

    @Test
    fun UnknownCarReturns404() = withApp { owner, _ ->
        assertEquals(HttpStatusCode.NotFound, tco("/cars/999/tco", owner).status)
    }

    @Test
    fun NegativeNonNumericOrEmptyKmPerYearReturns400() = withApp { owner, _ ->
        for (km in listOf("-1", "abc", "12.5", "")) {
            assertEquals(HttpStatusCode.BadRequest, tco("/cars/7/tco?kmPerYear=$km", owner).status, "kmPerYear=$km")
        }
        assertEquals(HttpStatusCode.BadRequest, tco("/cars/abc/tco", owner).status)
    }

    private fun body(rate: Double = 40.0, extra: String = "") = """{
        "type":"BEV","brand":"Test Motors","licensePlate":"EV-100","mileage":5000,"rate":$rate,
        "location":{"latitude":52.3676,"longitude":4.9041},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue","capacity":75.0,"consumption":16.2$extra
    }"""

    private suspend fun ApplicationTestBuilder.send(method: io.ktor.http.HttpMethod, path: String, token: String, json: String) =
        client.request(path) {
            this.method = method
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    @Test
    fun PostStoresTheCostFieldsAndAZeroCarStillShowsThem() = withApp { owner, _ ->
        val withCosts = send(io.ktor.http.HttpMethod.Post, "/cars", owner, body(extra = ""","purchasePrice":25000.5,"insurancePerYear":600,"roadTaxPerYear":120.25,"maintenancePerYear":450"""))
        assertEquals(HttpStatusCode.Created, withCosts.status)
        val json = Json.parseToJsonElement(withCosts.bodyAsText()).jsonObject
        assertEquals(25000.5, json["purchasePrice"]!!.jsonPrimitive.double)
        assertEquals(120.25, json["roadTaxPerYear"]!!.jsonPrimitive.double)

        val withoutCosts = Json.parseToJsonElement(send(io.ktor.http.HttpMethod.Post, "/cars", owner, body().replace("EV-100", "EV-101")).bodyAsText()).jsonObject
        assertEquals(0.0, withoutCosts["purchasePrice"]!!.jsonPrimitive.double) // a default value is still in the JSON
        assertEquals(0.0, withoutCosts["maintenancePerYear"]!!.jsonPrimitive.double)
    }

    @Test
    fun NegativeCostFieldReturns400() = withApp { owner, _ ->
        assertEquals(HttpStatusCode.BadRequest, send(io.ktor.http.HttpMethod.Post, "/cars", owner, body(extra = ""","purchasePrice":-1""")).status)
    }

    @Test
    fun PutWithoutCostFieldsKeepsThem() = withApp { owner, _ ->
        // car 7 has purchasePrice 10000 and 500 + 200 + 300 of yearly costs
        val response = send(io.ktor.http.HttpMethod.Put, "/cars/7", owner, body(rate = 41.0).replace("EV-100", "EV-1"))

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(10_000.0, json["purchasePrice"]!!.jsonPrimitive.double)
        assertEquals(300.0, json["maintenancePerYear"]!!.jsonPrimitive.double)

        val changed = send(io.ktor.http.HttpMethod.Put, "/cars/7", owner, body(extra = ""","purchasePrice":0""").replace("EV-100", "EV-1"))
        assertEquals(0.0, Json.parseToJsonElement(changed.bodyAsText()).jsonObject["purchasePrice"]!!.jsonPrimitive.double)
    }

    @Test
    fun TcoIsRoundedToTwoDecimals() = withApp { owner, _ ->
        // 2000 + 0.0525 * 12345 = 2648.1125
        val json = Json.parseToJsonElement(tco("/cars/7/tco?kmPerYear=12345", owner).bodyAsText()).jsonObject
        assertEquals(2648.11, json["tcoPerYear"]!!.jsonPrimitive.double, 1e-9)
    }

    @Test
    fun KmPerYearBeyondIntReturns400() = withApp { owner, _ ->
        assertEquals(HttpStatusCode.BadRequest, tco("/cars/7/tco?kmPerYear=99999999999", owner).status)
    }
}
