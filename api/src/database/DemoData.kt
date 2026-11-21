package com.example.database

import com.example.accounts.User
import com.example.accounts.UserRepository
import com.example.cars.BEVCar
import com.example.cars.BodyStyle
import com.example.cars.Car
import com.example.cars.CarRepository
import com.example.cars.CarStatus
import com.example.cars.FuelType
import com.example.cars.ICECar
import com.example.cars.Location
import com.example.cars.Transmission
import com.example.images.ImageRepository
import com.example.images.ImageType
import com.example.organizations.OrganizationRepository
import com.example.ratings.RatingRepository
import com.example.reservations.Daypart
import com.example.reservations.ReservationRepository
import com.example.trips.TripRepository
import com.example.trips.bonusPoints
import com.example.trips.calculateScore
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** The password of every demo account. Only ever seeded in development mode. */
const val DEMO_PASSWORD = "drive-calmly"

/** The demo accounts, in the order they are made. The first one tells whether the seed already ran. */
val DEMO_ACCOUNTS = listOf(
    "Sanne de Vries" to "sanne@rent-a-car.test", // renter with the most trips
    "Daan Bakker" to "daan@rent-a-car.test", // owns two cars, member of Avans Rentals
    "Noor Jansen" to "noor@rent-a-car.test", // admin of Avans Rentals
    "Milan Visser" to "milan@rent-a-car.test",
    "Lotte Smit" to "lotte@rent-a-car.test",
)

private val COMMENTS = listOf(
    "Clean car and easy pick-up.", "Drives great.", "Fully charged, as promised.", "A bit hard to find the parking spot.",
    "Would rent again.", "Smelled a little of smoke.", null, null,
)

/**
 * Fills a fresh database with data to try the app with: the demo accounts (password [DEMO_PASSWORD]), an organization with its own
 * cars, two private cars, a month of finished trips with ratings, a few reservations in the coming days, and a picture for every car.
 * Dates are relative to [today], so the data never goes stale. Does nothing when the first demo account already exists, so it runs once.
 * Uses the repositories, so passwords, scores and points work exactly as for real users.
 */
fun seedDemoData(
    users: UserRepository,
    organizations: OrganizationRepository,
    cars: CarRepository,
    reservations: ReservationRepository,
    trips: TripRepository,
    ratings: RatingRepository,
    images: ImageRepository,
    today: LocalDate,
    zone: ZoneId,
) {
    val (firstName, firstEmail) = DEMO_ACCOUNTS.first()
    val sanne = users.register(firstName, firstEmail, DEMO_PASSWORD) ?: return
    val (daan, noor, milan, lotte) = DEMO_ACCOUNTS.drop(1).map { (name, email) ->
        users.register(name, email, DEMO_PASSWORD) ?: error("demo account $email exists although $firstEmail did not")
    }

    val avans = organizations.create("Avans Rentals", noor.id)
    organizations.accept(organizations.invite(avans.id), daan.id)

    // Daan's Fiat is past its service, so My cars has something to warn about.
    cars.addCar(BEVCar(0, daan.id, "Kia EV6", "N-517-KD", 21800, CarStatus.Available, 45.0, Location(51.5866, 4.7760), 5, BodyStyle.SUV, 2023, 168, Transmission.Automatic, "Grey", 77.4, 16.5, 46000.0, 1150.0, 0.0, 420.0))
    cars.addCar(ICECar(0, daan.id, "Fiat 500", "S-208-FT", 64300, CarStatus.Available, 24.0, Location(51.5890, 4.7820), 3, BodyStyle.Hatchback, 2018, 51, Transmission.Manual, "White", FuelType.Petrol, 35.0, 4.9, 12500.0, 640.0, 360.0, 380.0, lastServiceKm = 48100))
    cars.addCar(BEVCar(0, null, "Polestar 2", "P-301-AR", 15200, CarStatus.Available, 58.0, Location(51.5853, 4.7929), 5, BodyStyle.Sedan, 2024, 220, Transmission.Automatic, "Black", 78.0, 17.0, 52000.0, 1300.0, 0.0, 450.0, organizationId = avans.id))
    cars.addCar(ICECar(0, null, "Skoda Octavia", "R-774-AV", 98400, CarStatus.Available, 36.0, Location(51.5857, 4.7932), 5, BodyStyle.Stationwagon, 2020, 110, Transmission.Automatic, "Silver", FuelType.Diesel, 45.0, 4.6, 28000.0, 820.0, 540.0, 610.0, organizationId = avans.id))

    val fleet = cars.allCars()
    for (car in fleet) images.add(car.id, ImageType.PNG, drawCar(car))

    // Fixed seed: the same demo data on every machine.
    val random = Random(42)
    val rentable = fleet.filter { it.status == CarStatus.Available }
    val renters = listOf(sanne to 9, milan to 6, lotte to 5, daan to 3, noor to 2)
    for ((renter, count) in renters) {
        val days = (1..28).shuffled(random).take(count)
        for (daysAgo in days) {
            val car = rentable.filter { it.ownerId != renter.id }.random(random)
            val daypart = Daypart.entries.random(random)
            finishedTrip(reservations, trips, ratings, renter, car, today.minusDays(daysAgo.toLong()), daypart, zone, random)
        }
    }

    // Coming days: Sanne has one today and one tomorrow; a few cars are taken, so "free on" leaves them out.
    val upcoming = listOf(
        Triple(sanne, 0L, Daypart.EVENING), Triple(sanne, 1L, Daypart.MORNING), Triple(milan, 0L, Daypart.MORNING),
        Triple(milan, 1L, Daypart.AFTERNOON), Triple(lotte, 2L, Daypart.EVENING), Triple(lotte, 0L, Daypart.AFTERNOON),
    )
    for ((index, booking) in upcoming.withIndex()) {
        val (renter, inDays, daypart) = booking
        reservations.reserve(rentable[index % rentable.size].id, renter.id, today.plusDays(inDays), daypart)
    }
}

/** A reservation on [date], started and ended in its daypart, scored like the app does, and rated three times out of four. */
private fun finishedTrip(
    reservations: ReservationRepository, trips: TripRepository, ratings: RatingRepository,
    renter: User, car: Car, date: LocalDate, daypart: Daypart, zone: ZoneId, random: Random,
) {
    val reservation = reservations.reserve(car.id, renter.id, date, daypart) ?: return // that daypart was already taken
    val startedAt = date.atTime(daypart.from).plusMinutes(random.nextLong(10, 90)).atZone(zone).toOffsetDateTime()
    val endedAt = startedAt.plusMinutes(random.nextLong(25, 150))
    val distanceKm = random.nextInt(8, 160)
    // Mostly calm, sometimes not, so the scores and the leaderboard differ.
    val events = if (random.nextInt(4) == 0) random.nextInt(4, 12) else random.nextInt(0, 3)
    val hardAccelerations = random.nextInt(events + 1)
    val score = calculateScore(distanceKm, hardAccelerations, events - hardAccelerations)
    trips.start(reservation.id, startedAt) ?: return
    trips.record(reservation.id, renter.id, distanceKm, score, bonusPoints(distanceKm, score), endedAt) ?: return
    if (random.nextInt(4) != 0) {
        val stars = if (score >= 80) random.nextInt(4, 6) else random.nextInt(2, 5)
        ratings.rate(reservation, stars, COMMENTS.random(random))
    }
}
