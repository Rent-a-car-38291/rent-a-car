package org.jetbrains.amper.ktor

import com.example.accounts.TokenRepository
import com.example.accounts.UserRepository
import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.cars.PostgresCarRepository
import com.example.cars.configureCars
import com.example.configureRouting
import com.example.configureErrors
import com.example.configureSerialization
import com.example.database.connectDatabase
import com.example.database.seedDemoData
import com.example.images.ImageRepository
import com.example.images.configureImages
import com.example.organizations.OrganizationRepository
import com.example.organizations.configureOrganizationReport
import com.example.organizations.configureOrganizations
import com.example.ratings.RatingRepository
import com.example.ratings.configureRatings
import com.example.reservations.ReservationRepository
import com.example.reservations.configureReservations
import com.example.trips.TripRepository
import com.example.trips.configureTrips
import com.example.reservations.ZONE
import io.ktor.server.application.*
import java.time.LocalDate
import java.nio.file.Path

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureErrors()
    configureRouting()
    val dataSource = connectDatabase()
    val users = UserRepository(dataSource)
    val tokens = TokenRepository(dataSource)
    configureSecurity(users, tokens)
    val organizations = OrganizationRepository(dataSource)
    val cars = PostgresCarRepository(dataSource)
    val images = ImageRepository(dataSource, Path.of(environment.config.property("images.directory").getString()))
    val reservations = ReservationRepository(dataSource)
    val ratings = RatingRepository(dataSource)
    configureCars(cars, organizations, images, reservations, ratings)
    configureImages(cars, organizations, images)
    configureReservations(cars, reservations)
    val trips = TripRepository(dataSource)
    configureTrips(reservations, trips, cars, organizations)
    configureRatings(reservations, ratings, cars)
    configureAccounts(users, organizations, tokens)
    configureOrganizations(organizations)
    configureOrganizationReport(organizations, cars, trips)
    // Demo accounts with a known password: development mode only, never on a real server.
    if (developmentMode && environment.config.propertyOrNull("seed.demoData")?.getString() == "true") {
        seedDemoData(users, organizations, cars, reservations, trips, ratings, images, LocalDate.now(ZONE), ZONE)
    }
}
