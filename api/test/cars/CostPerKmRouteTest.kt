package com.example.cars

import com.example.accounts.UserRepository
import com.example.accounts.configureSecurity
import com.example.accounts.TokenRepository
import com.example.configureSerialization
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import kotlin.test.*

class CostPerKmRouteTest {
    private val repository = InMemoryCarRepository().apply {
        cars += BEVCar(7, 1, "Tesla", "EV-1", 0, CarStatus.Available, 55.0, Location(0.0, 0.0), 4, BodyStyle.Sedan, 2021, 208, Transmission.Automatic, "White", 60.0, 15.0)
        cars += ICECar(8, 1, "Opel", "IC-1", 0, CarStatus.Available, 30.0, Location(0.0, 0.0), 5, BodyStyle.Hatchback, 2019, 90, Transmission.Manual, "Grey", FuelType.Petrol, 45.0, 6.37)
    }

    private fun withApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        useTestDatabase()
        application {
            configureSerialization()
            configureSecurity(UserRepository(TestDatabase.dataSource), TokenRepository(TestDatabase.dataSource))
            configureCars(repository, OrganizationRepository(TestDatabase.dataSource))
        }
        block()
    }

    @Test
    fun ReturnsCostPerKmWithoutLogin() = withApp {
        val response = client.get("/cars/7/cost-per-km")

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(7, json["carId"]!!.jsonPrimitive.int)
        assertEquals(0.0525, json["costPerKm"]!!.jsonPrimitive.double, 1e-9) // 15 kWh / 100 km * 0.35 EUR
        assertEquals("EUR", json["currency"]!!.jsonPrimitive.content)
    }

    @Test
    fun PetrolCarUsesPetrolPriceRoundedToFourDecimals() = withApp {
        val json = Json.parseToJsonElement(client.get("/cars/8/cost-per-km").bodyAsText()).jsonObject

        assertEquals(0.1338, json["costPerKm"]!!.jsonPrimitive.double, 1e-9) // 6.37 l / 100 km * 2.10 EUR = 0.13377
    }

    @Test
    fun UnknownCarReturns404() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.get("/cars/999/cost-per-km").status)
    }

    @Test
    fun NonNumericIdReturns400() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.get("/cars/abc/cost-per-km").status)
    }

    @Test
    fun QuoteAddsEnergyCostAndRentalRate() = withApp {
        val response = client.get("/cars/7/quote?distanceKm=120")

        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(7, json["carId"]!!.jsonPrimitive.int)
        assertEquals(120.0, json["distanceKm"]!!.jsonPrimitive.double)
        assertEquals(6.3, json["energyCost"]!!.jsonPrimitive.double, 1e-9) // 0.0525 EUR per km * 120
        assertEquals(55.0, json["rentalRate"]!!.jsonPrimitive.double)
        assertEquals(61.3, json["total"]!!.jsonPrimitive.double, 1e-9)
        assertEquals("EUR", json["currency"]!!.jsonPrimitive.content)
    }

    @Test
    fun QuoteWithMissingOrOutOfRangeDistanceReturns400() = withApp {
        listOf("/cars/7/quote", "/cars/7/quote?distanceKm=abc", "/cars/7/quote?distanceKm=0", "/cars/7/quote?distanceKm=10001", "/cars/abc/quote?distanceKm=5")
            .forEach { assertEquals(HttpStatusCode.BadRequest, client.get(it).status, it) }
    }

    @Test
    fun QuoteForUnknownCarReturns404() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.get("/cars/999/quote?distanceKm=10").status)
    }
}
