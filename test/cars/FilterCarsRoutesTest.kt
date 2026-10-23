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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class FilterCarsRoutesTest {
    private fun car(type: CarType, plate: String, rate: Double, lat: Double, lon: Double) = AddCarRequest(
        type, "Test", plate, 100, rate, Location(lat, lon), 4, BodyStyle.Sedan, 2020, 100, Transmission.Manual, "Red",
        fuelType = FuelType.Petrol.takeIf { type == CarType.ICE }, capacity = 50.0, consumption = 5.0,
    ).toCar(1)

    // Breda centre, about 2 km from Breda centre, and Amsterdam (far away).
    private val repository = InMemoryCarRepository().apply { cars.addAll(listOf(
        car(CarType.BEV, "FAR", 30.0, 52.37, 4.90),
        car(CarType.ICE, "NEAR", 50.0, 51.60, 4.80),
        car(CarType.BEV, "NEARER", 40.0, 51.59, 4.78),
    )) }

    private suspend fun plates(query: String, block: suspend (HttpStatusCode, List<String>) -> Unit) = testApplication {
        useTestDatabase()
        application {
            configureSerialization()
            configureSecurity(UserRepository(TestDatabase.dataSource), TokenRepository(TestDatabase.dataSource))
            configureCars(repository, OrganizationRepository(TestDatabase.dataSource))
        }
        val response = client.get("/cars$query")
        val plates = if (response.status == HttpStatusCode.OK) {
            Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["licensePlate"]!!.jsonPrimitive.content }
        } else emptyList()
        block(response.status, plates)
    }

    private fun check(query: String, expected: List<String>) = kotlinx.coroutines.runBlocking {
        plates(query) { status, plates ->
            assertEquals(HttpStatusCode.OK, status, query)
            assertEquals(expected, plates, query)
        }
    }

    private fun checkBadRequest(query: String) = kotlinx.coroutines.runBlocking {
        plates(query) { status, _ -> assertEquals(HttpStatusCode.BadRequest, status, query) }
    }

    @Test
    fun WithoutFilterReturnsAllCars() = check("", listOf("FAR", "NEAR", "NEARER"))

    @Test
    fun FiltersOnType() = check("?type=BEV", listOf("FAR", "NEARER"))

    @Test
    fun FiltersOnMaxRate() = check("?maxRate=40", listOf("FAR", "NEARER"))

    @Test
    fun FiltersOnDistanceAndSortsNearestFirst() = check("?lat=51.59&lon=4.78&maxDistanceKm=5", listOf("NEARER", "NEAR"))

    @Test
    fun SortsNearestFirstWithoutMaxDistance() = check("?lat=51.59&lon=4.78", listOf("NEARER", "NEAR", "FAR"))

    @Test
    fun CombinesFilters() = check("?type=BEV&maxRate=35&lat=51.59&lon=4.78&maxDistanceKm=500", listOf("FAR"))

    @Test
    fun InvalidFiltersReturnBadRequest() {
        checkBadRequest("?type=SUV")
        checkBadRequest("?type=bev")
        checkBadRequest("?maxRate=cheap")
        checkBadRequest("?lat=abc&lon=4.78&maxDistanceKm=5")
        checkBadRequest("?maxDistanceKm=5")
        checkBadRequest("?lat=51.59&maxDistanceKm=5")
        checkBadRequest("?lat=51.59&lon=4.78&maxDistanceKm=far")
        checkBadRequest("?lat=95&lon=4.78")
    }

    @Test
    fun SortsByRateInBothDirections() {
        check("?sort=rate", listOf("FAR", "NEARER", "NEAR"))
        check("?sort=-rate", listOf("NEAR", "NEARER", "FAR"))
    }

    // The test cars all use 5 per 100 km: 0.0175 EUR per km for the BEVs at the standard prices and 0.105 for the petrol car.
    @Test
    fun SortsByCostPerKmWithTheCheapestFirstAndByRange() {
        check("?sort=costPerKm", listOf("FAR", "NEARER", "NEAR"))
        check("?sort=-costPerKm", listOf("NEAR", "FAR", "NEARER"))
        check("?sort=range", listOf("FAR", "NEAR", "NEARER"))
    }

    @Test
    fun FiltersOnBodyStyleTransmissionFuelAndRange() {
        check("?bodyStyle=Sedan", listOf("FAR", "NEAR", "NEARER"))
        check("?bodyStyle=SUV", emptyList())
        check("?transmission=Manual", listOf("FAR", "NEAR", "NEARER"))
        check("?transmission=Automatic", emptyList())
        check("?fuelType=Petrol", listOf("NEAR"))
        check("?minRange=1000", listOf("FAR", "NEAR", "NEARER")) // capacity 50 per 5 per 100 km is 1000 km
        check("?minRange=1000.1", emptyList())
    }

    @Test
    fun NewFiltersCombineWithTheOldOnesAndALocationStillSortsNearestFirst() {
        check("?type=BEV&bodyStyle=Sedan&sort=-rate", listOf("NEARER", "FAR"))
        check("?sort=-rate&lat=51.59&lon=4.78", listOf("NEARER", "NEAR", "FAR"))
    }

    @Test
    fun InvalidSortsAndNewFiltersReturnBadRequest() {
        checkBadRequest("?sort=price")
        checkBadRequest("?sort=")
        checkBadRequest("?bodyStyle=Truck")
        checkBadRequest("?transmission=cvt")
        checkBadRequest("?fuelType=Hydrogen")
        checkBadRequest("?minRange=-1")
        checkBadRequest("?minRange=far")
    }
}
