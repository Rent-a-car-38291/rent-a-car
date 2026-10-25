package com.example.ratings

import com.example.accounts.UserRepository
import com.example.database.TestDatabase
import com.example.database.useTestDatabase
import com.example.organizations.OrganizationRepository
import com.example.reservations.Daypart
import com.example.reservations.ReservationRepository
import com.example.trips.TripRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import org.jetbrains.amper.ktor.module
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.*

class RatingRoutesTest {
    private val reservations = ReservationRepository(TestDatabase.dataSource)
    private val trips = TripRepository(TestDatabase.dataSource)

    @BeforeTest
    fun reset() {
        OrganizationRepository(TestDatabase.dataSource).clear() // also removes the organization cars
        UserRepository(TestDatabase.dataSource).clear() // and with the users their cars, reservations, trips and ratings
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

    private fun carRequest(licensePlate: String) = """{
        "type":"BEV","brand":"Test Motors","licensePlate":"$licensePlate","mileage":1,"rate":45.5,
        "location":{"latitude":52.3,"longitude":4.9},"doors":4,"bodyStyle":"Sedan","modelYear":2024,
        "power":150,"transmission":"Automatic","color":"Blue","capacity":75.0,"consumption":16.2}"""

    private suspend fun HttpClient.addCar(owner: User, licensePlate: String): Int {
        val response = postJson("/cars", carRequest(licensePlate), owner.token)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.json().jsonObject["id"]!!.jsonPrimitive.int
    }

    // The API only takes a trip for a reservation that has started, but a test needs trips on chosen dates, so it saves them directly.
    private fun finishedTrip(carId: Int, user: User, date: String = "2026-01-01", daypart: Daypart = Daypart.MORNING): Int {
        val reservation = reservations.reserve(carId, user.id, LocalDate.parse(date), daypart)!!
        trips.record(reservation.id, user.id, 10, 100, 0)
        return reservation.id
    }

    private suspend fun HttpClient.rate(reservationId: Any, body: String, token: String?) = postJson("/reservations/$reservationId/rating", body, token)

    private suspend fun HttpClient.ratings(carId: Int) = get("/cars/$carId/ratings")

    @Test
    fun TheRenterRatesTheCarAfterTheTrip() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "RATE-1")
        val reservation = finishedTrip(car, jan)

        val response = client.rate(reservation, """{"stars":4,"comment":"Clean and quiet"}""", jan.token)

        assertEquals(HttpStatusCode.Created, response.status)
        val rating = response.json().jsonObject
        assertEquals(reservation, rating["reservationId"]!!.jsonPrimitive.int)
        assertEquals(car, rating["carId"]!!.jsonPrimitive.int)
        assertEquals(4, rating["stars"]!!.jsonPrimitive.int)
        assertEquals("Clean and quiet", rating["comment"]!!.jsonPrimitive.content)
    }

    @Test
    fun TheCommentIsOptional() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = finishedTrip(client.addCar(jan, "RATE-2"), jan)

        val response = client.rate(reservation, """{"stars":5}""", jan.token)

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(JsonNull, response.json().jsonObject["comment"])
    }

    @Test
    fun ASecondRatingOfTheSameReservationGives409AndKeepsTheFirst() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "RATE-3")
        val reservation = finishedTrip(car, jan)
        client.rate(reservation, """{"stars":2}""", jan.token)

        val response = client.rate(reservation, """{"stars":5}""", jan.token)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(2, client.ratings(car).json().jsonObject["ratings"]!!.jsonArray.single().jsonObject["stars"]!!.jsonPrimitive.int)
    }

    @Test
    fun SeveralRatingsAtOnceGiveExactlyOne201() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = finishedTrip(client.addCar(jan, "RATE-4"), jan)

        val statuses = coroutineScope { (1..8).map { stars -> async { client.rate(reservation, """{"stars":${stars % 5 + 1}}""", jan.token).status } }.awaitAll() }

        assertEquals(1, statuses.count { it == HttpStatusCode.Created })
        assertEquals(7, statuses.count { it == HttpStatusCode.Conflict })
    }

    @Test
    fun AReservationWithoutATripGives409() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reservations.reserve(client.addCar(jan, "RATE-5"), jan.id, LocalDate.parse("2026-01-01"), Daypart.MORNING)!!.id

        assertEquals(HttpStatusCode.Conflict, client.rate(reservation, """{"stars":5}""", jan.token).status)
    }

    @Test
    fun AStartedTripThatIsNotEndedIsNotFinishedSoItGives409() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = reservations.reserve(client.addCar(jan, "RATE-6"), jan.id, LocalDate.parse("2026-01-01"), Daypart.MORNING)!!.id
        assertNotNull(trips.start(reservation, OffsetDateTime.now()))

        assertEquals(HttpStatusCode.Conflict, client.rate(reservation, """{"stars":5}""", jan.token).status)
    }

    @Test
    fun InvalidStarsOrCommentGive400() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = finishedTrip(client.addCar(jan, "RATE-7"), jan)

        listOf(
            """{"stars":0}""", """{"stars":6}""", """{"stars":-3}""", """{"stars":3.5}""", """{"stars":"five"}""", """{"comment":"no stars"}""",
            """{"stars":3,"comment":"${"a".repeat(501)}"}""",
        ).forEach { assertEquals(HttpStatusCode.BadRequest, client.rate(reservation, it, jan.token).status, it) }
        assertEquals(HttpStatusCode.Created, client.rate(reservation, """{"stars":3,"comment":"${"a".repeat(500)}"}""", jan.token).status)
    }

    @Test
    fun ANonNumericReservationIdGives400() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.BadRequest, client.rate("abc", """{"stars":3}""", jan.token).status)
    }

    @Test
    fun WithoutATokenTheAnswerIs401() = withApp {
        val jan = client.signUp("jan@example.com")
        val reservation = finishedTrip(client.addCar(jan, "RATE-8"), jan)

        assertEquals(HttpStatusCode.Unauthorized, client.rate(reservation, """{"stars":3}""", null).status)
    }

    @Test
    fun SomeoneElsesReservationGives403() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val reservation = finishedTrip(client.addCar(jan, "RATE-9"), jan)

        assertEquals(HttpStatusCode.Forbidden, client.rate(reservation, """{"stars":3}""", piet.token).status)
    }

    @Test
    fun AnUnknownReservationGives404() = withApp {
        val jan = client.signUp("jan@example.com")

        assertEquals(HttpStatusCode.NotFound, client.rate(999999, """{"stars":3}""", jan.token).status)
    }

    @Test
    fun TheRatingsOfACarShowTheAverageTheCountAndNoRenterData() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val car = client.addCar(jan, "RATE-10")
        client.rate(finishedTrip(car, jan, "2026-01-01"), """{"stars":5,"comment":"Great"}""", jan.token)
        client.rate(finishedTrip(car, piet, "2026-01-02"), """{"stars":2}""", piet.token)
        client.rate(finishedTrip(car, jan, "2026-01-03"), """{"stars":2}""", jan.token)

        val response = client.ratings(car)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json().jsonObject
        assertEquals(car, body["carId"]!!.jsonPrimitive.int)
        assertEquals(3.0, body["averageRating"]!!.jsonPrimitive.double)
        assertEquals(3, body["ratingCount"]!!.jsonPrimitive.int)
        val ratings = body["ratings"]!!.jsonArray
        assertEquals(listOf(2, 2, 5), ratings.map { it.jsonObject["stars"]!!.jsonPrimitive.int }) // newest first
        ratings.forEach { assertEquals(setOf("id", "reservationId", "carId", "stars", "comment"), it.jsonObject.keys) }
    }

    @Test
    fun ACarWithoutRatingsHasANullAverageAndAnEmptyList() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "RATE-11")

        val body = client.ratings(car).json().jsonObject

        assertEquals(JsonNull, body["averageRating"])
        assertEquals(0, body["ratingCount"]!!.jsonPrimitive.int)
        assertEquals(0, body["ratings"]!!.jsonArray.size)
    }

    @Test
    fun RatingsOfAnUnknownCarGive404AndOfANonNumericIdGive400() = withApp {
        assertEquals(HttpStatusCode.NotFound, client.ratings(999999).status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/cars/abc/ratings").status)
    }

    @Test
    fun TheRatingsOfOneCarDoNotCountForAnother() = withApp {
        val jan = client.signUp("jan@example.com")
        val rated = client.addCar(jan, "RATE-12")
        val other = client.addCar(jan, "RATE-13")
        client.rate(finishedTrip(rated, jan), """{"stars":5}""", jan.token)

        assertEquals(0, client.ratings(other).json().jsonObject["ratingCount"]!!.jsonPrimitive.int)
    }

    @Test
    fun TheCarsShowTheirAverageRatingAndNullWithoutRatings() = withApp {
        val jan = client.signUp("jan@example.com")
        val rated = client.addCar(jan, "RATE-14")
        val unrated = client.addCar(jan, "RATE-15")
        client.rate(finishedTrip(rated, jan, "2026-01-01"), """{"stars":4}""", jan.token)
        client.rate(finishedTrip(rated, jan, "2026-01-02"), """{"stars":5}""", jan.token)

        val cars = client.get("/cars").json().jsonArray.associateBy { it.jsonObject["id"]!!.jsonPrimitive.int }

        assertEquals(4.5, cars[rated]!!.jsonObject["averageRating"]!!.jsonPrimitive.double)
        assertEquals(JsonNull, cars[unrated]!!.jsonObject["averageRating"])
        assertTrue(cars.values.all { "averageRating" in it.jsonObject }) // the seeded cars too, as null
    }

    @Test
    fun OneCarShowsItsAverageRatingAndNullWithoutRatings() = withApp {
        val jan = client.signUp("jan@example.com")
        val rated = client.addCar(jan, "RATE-16")
        val unrated = client.addCar(jan, "RATE-17")
        client.rate(finishedTrip(rated, jan), """{"stars":3}""", jan.token)

        val ratedJson = client.get("/cars/$rated") { bearerAuth(jan.token) }.json().jsonObject
        val unratedJson = client.get("/cars/$unrated") { bearerAuth(jan.token) }.json().jsonObject

        assertEquals(3.0, ratedJson["averageRating"]!!.jsonPrimitive.double)
        assertEquals(JsonNull, unratedJson["averageRating"])
        assertTrue("images" in ratedJson) // the photo urls are still there
    }

    @Test
    fun DeletingTheCarRemovesItsRatings() = withApp {
        val jan = client.signUp("jan@example.com")
        val car = client.addCar(jan, "RATE-18")
        client.rate(finishedTrip(car, jan), """{"stars":3}""", jan.token)

        assertEquals(HttpStatusCode.NoContent, client.delete("/cars/$car") { bearerAuth(jan.token) }.status)

        assertEquals(emptyList(), RatingRepository(TestDatabase.dataSource).forCar(car))
    }

    @Test
    fun DeletingTheAccountOfTheRenterRemovesTheirRatings() = withApp {
        val jan = client.signUp("jan@example.com")
        val piet = client.signUp("piet@example.com")
        val car = client.addCar(jan, "RATE-19")
        client.rate(finishedTrip(car, piet, "2026-01-01"), """{"stars":1}""", piet.token)
        client.rate(finishedTrip(car, jan, "2026-01-02"), """{"stars":5}""", jan.token)

        assertEquals(HttpStatusCode.NoContent, client.delete("/accounts/${piet.id}") { bearerAuth(piet.token) }.status)

        val body = client.ratings(car).json().jsonObject
        assertEquals(1, body["ratingCount"]!!.jsonPrimitive.int)
        assertEquals(5.0, body["averageRating"]!!.jsonPrimitive.double)
    }
}
