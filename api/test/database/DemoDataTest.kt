package com.example.database

import com.example.Paging
import com.example.accounts.UserRepository
import com.example.cars.PostgresCarRepository
import com.example.cars.serviceDue
import com.example.images.ImageRepository
import com.example.organizations.OrganizationRepository
import com.example.ratings.RatingRepository
import com.example.reservations.ReservationFilter
import com.example.reservations.ReservationRepository
import com.example.reservations.ReservationStatus
import com.example.trips.TripRepository
import io.ktor.server.config.*
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import javax.imageio.ImageIO
import kotlin.test.*

class DemoDataTest {
    // Its own database: the seed fills tables the other tests expect to control.
    private val dataSource = TestDatabase.separate("demo_data_test")
    private val users = UserRepository(dataSource)
    private val organizations = OrganizationRepository(dataSource)
    private val cars = PostgresCarRepository(dataSource)
    private val reservations = ReservationRepository(dataSource)
    private val trips = TripRepository(dataSource)
    private val ratings = RatingRepository(dataSource)
    private val images = ImageRepository(dataSource, Files.createTempDirectory("demo-images").also { it.toFile().deleteOnExit() })
    private val today = LocalDate.of(2026, 11, 21)
    private val zone = ZoneId.of("Europe/Amsterdam")

    private fun seed() = seedDemoData(users, organizations, cars, reservations, trips, ratings, images, today, zone)

    @Test
    fun TheDemoDataFillsEveryPartOfTheAppAndRunsOnlyOnce() {
        seed()
        val carCount = cars.allCars().size
        val leaderboard = trips.leaderboard(Paging(100, 0))
        seed()

        // Every demo account logs in with the demo password.
        for ((_, email) in DEMO_ACCOUNTS) assertNotNull(users.authenticate(email, DEMO_PASSWORD), email)

        // The second run added nothing.
        assertEquals(carCount, cars.allCars().size)
        assertEquals(leaderboard, trips.leaderboard(Paging(100, 0)))

        // 13 sample cars, 2 of Daan, 2 of the organization; Daan's Fiat needs service.
        assertEquals(17, carCount)
        val daan = users.authenticate("daan@rent-a-car.test", DEMO_PASSWORD)!!
        assertEquals(2, cars.allCars().count { it.ownerId == daan.id })
        assertTrue(cars.allCars().single { it.licensePlate == "S-208-FT" }.serviceDue)
        val noor = users.authenticate("noor@rent-a-car.test", DEMO_PASSWORD)!!
        assertFalse(organizations.isLastAdmin(daan.id))
        assertTrue(organizations.isLastAdmin(noor.id))

        // Every demo account drove, and the leaderboard has them all.
        assertEquals(DEMO_ACCOUNTS.size, leaderboard.size)
        val sanne = users.authenticate("sanne@rent-a-car.test", DEMO_PASSWORD)!!
        assertTrue(trips.stats(sanne.id)!!.tripCount >= 5)

        // Ratings on several cars, a picture on every car.
        assertTrue(ratings.averages().size >= 5)
        for (car in cars.allCars()) {
            val image = images.forCar(car.id).single()
            assertNotNull(ImageIO.read(images.file(image.url.substringAfterLast('/'))!!.toFile()), car.brand)
        }

        // Sanne has a reservation today and one tomorrow.
        val upcoming = reservations.list(sanne.id, ReservationFilter(ReservationStatus.RESERVED, 10, 0)).map { LocalDate.parse(it.date) }
        assertEquals(listOf(today, today.plusDays(1)), upcoming.sorted())
    }

    @Test
    fun TheSeedIsOnByDefaultInTheServerConfig() {
        assertEquals("true", ConfigLoader.load("application.yaml").property("seed.demoData").getString())
    }
}
