package com.example

import com.example.domain.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        staticResources("static", "static")

        get("/cars") {
            val iceCar = ICECar(1, "Peugeot", "85-GHB-2",
                217349, CarStatus.Available, 25.00, Location(10.00, 29.294),
                5, BodyStyle.Hatchback, 2008, 95, Transmission.Manual, "Black",
                FuelType.Petrol, 50.0, 6.10)

            val bevCar = BEVCar(
                2, "Tesla Model 3", "K-123-BV",
                48210, CarStatus.Available, 55.00, Location(52.3676, 4.9041),
                4, BodyStyle.Sedan, 2021, 208, Transmission.Automatic, "White",
                60.0, 14.5
            )

            val fcevCar = FCEVCar(
            3, "Toyota Mirai", "XT-482-H",
            32500, CarStatus.Available, 70.00, Location(51.9244, 4.4777),
            4, BodyStyle.Sedan, 2022, 134, Transmission.Automatic, "Blue",
            5.6, 0.76
            )

            val cars = listOf(iceCar, bevCar, fcevCar)

            call.respond(cars)
        }
    }
}