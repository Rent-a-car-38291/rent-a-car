package org.jetbrains.amper.ktor

import com.example.accounts.UserRepository
import com.example.accounts.configureAccounts
import com.example.accounts.configureSecurity
import com.example.cars.PostgresCarRepository
import com.example.cars.configureCars
import com.example.configureRouting
import com.example.configureSerialization
import com.example.database.connectDatabase
import com.example.images.ImageRepository
import com.example.images.configureImages
import com.example.organizations.OrganizationRepository
import com.example.organizations.configureOrganizations
import com.example.reservations.ReservationRepository
import com.example.reservations.configureReservations
import com.example.trips.TripRepository
import com.example.trips.configureTrips
import io.ktor.server.application.*
import java.nio.file.Path

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureRouting()
    val dataSource = connectDatabase()
    val users = UserRepository(dataSource)
    configureSecurity(users)
    val organizations = OrganizationRepository(dataSource)
    val cars = PostgresCarRepository(dataSource)
    val images = ImageRepository(dataSource, Path.of(environment.config.property("images.directory").getString()))
    val reservations = ReservationRepository(dataSource)
    configureCars(cars, organizations, images, reservations)
    configureImages(cars, organizations, images)
    configureReservations(cars, reservations)
    configureTrips(reservations, TripRepository(dataSource), cars, organizations)
    configureAccounts(users, organizations)
    configureOrganizations(organizations)
}
