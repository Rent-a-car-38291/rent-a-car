package com.example.reservations

import com.example.accounts.UserRepository
import com.example.cars.CarStatus
import com.example.cars.PostgresCarRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
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
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import kotlin.test.*

class FreeCarsRoutesTest {
    private val cars = PostgresCarRepository(TestDatabase.dataSource)
    private val reservedCar = cars.allCars().first { it.status == CarStatus.Available }.id
    private val maintenanceCar = cars.allCars().first { it.status == CarStatus.Maintenance }.id

    @BeforeTest
    fun reset() = UserRepository(TestDatabase.dataSource).clear()

    private fun withApp(block: suspend ApplicationTestBuilder.(token: String) -> Unit) = testApplication {
        useTestDatabase()
        application { module() }
        client.post("/accounts") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Jan","email":"jan@example.com","password":"supersecret"}""")
        }
        val login = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"jan@example.com","password":"supersecret"}""")
        }
        block(Json.parseToJsonElement(login.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content)
    }

    private suspend fun ApplicationTestBuilder.reserve(carId: Int, date: String, daypart: String, token: String) {
        val response = client.post("/cars/$carId/reservations") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"date":"$date","daypart":"$daypart"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    private suspend fun ApplicationTestBuilder.carIds(query: String): Set<Int> {
        val response = client.get("/cars?$query")
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }.toSet()
    }

    @Test
    fun AReservedCarIsLeftOutOnlyForThatDateAndDaypart() = withApp { token ->
        reserve(reservedCar, "2030-05-01", "MORNING", token)

        assertFalse(reservedCar in carIds("date=2030-05-01&daypart=MORNING"))
        assertTrue(reservedCar in carIds("date=2030-05-01&daypart=AFTERNOON"))
        assertTrue(reservedCar in carIds("date=2030-05-02&daypart=MORNING"))
    }

    @Test
    fun ACarInMaintenanceIsLeftOutOfASearchButNotOfTheFullList() = withApp {
        assertFalse(maintenanceCar in carIds("date=2030-05-03&daypart=MORNING"))
        assertTrue(maintenanceCar in carIds(""))
    }

    @Test
    fun TheSlotCombinesWithTheOtherFilters() = withApp { token ->
        reserve(reservedCar, "2030-05-04", "EVENING", token)
        val ice = carIds("type=ICE")

        val free = carIds("type=ICE&date=2030-05-04&daypart=EVENING")

        assertTrue(free.isNotEmpty())
        assertTrue(ice.containsAll(free))
        assertFalse(reservedCar in free)
    }

    @Test
    fun TodayCanBeSearched() = withApp {
        assertTrue(carIds("date=${LocalDate.now(ZONE)}&daypart=EVENING").isNotEmpty())
    }

    @Test
    fun OnlyOneParameterAnInvalidValueOrAPastDateGives400() = withApp {
        listOf(
            "date=2030-05-05",
            "daypart=MORNING",
            "date=nonsense&daypart=MORNING",
            "date=2030-05-05&daypart=NIGHT",
            "date=${LocalDate.now(ZONE).minusDays(1)}&daypart=MORNING",
        ).forEach { assertEquals(HttpStatusCode.BadRequest, client.get("/cars?$it").status, it) }
    }
}
