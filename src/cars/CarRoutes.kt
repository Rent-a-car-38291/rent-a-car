package com.example.cars

import com.example.accounts.User
import com.example.images.ImageRepository
import com.example.organizations.OrganizationRepository
import com.example.reservations.ReservationRepository
import com.example.reservations.ZONE
import com.example.reservations.parseFreeSlot
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private fun Double.roundTo(decimals: Int) = BigDecimal.valueOf(this).setScale(decimals, RoundingMode.HALF_UP).toDouble()

// reservations is optional like images: without it the date and daypart parameters are ignored.
fun Application.configureCars(
    repository: CarRepository,
    organizations: OrganizationRepository,
    images: ImageRepository? = null,
    reservations: ReservationRepository? = null,
) {
    routing {
        get("/cars") {
            val query = call.request.queryParameters
            val slot = parseFreeSlot(query["date"], query["daypart"], LocalDate.now(ZONE)).getOrElse {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            var all = repository.allCars()
            if (slot != null && reservations != null) {
                val reserved = reservations.reservedCarIds(slot.date, slot.daypart)
                all = all.filter { it.status != CarStatus.Maintenance && it.id !in reserved }
            }
            val cars = filterCars(all, query)
            if (cars == null) call.respond(HttpStatusCode.BadRequest) else call.respond(cars)
        }

        get("/cars/{id}/cost-per-km") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val car = repository.findCar(id)
            if (car == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(CostPerKmResponse(id, car.costPerKm(EnergyPrices.STANDARD).roundTo(4), "EUR"))
        }

        authenticate {
            post("/cars") {
                val user = call.principal<User>()!!
                val request = call.receive<AddCarRequest>()
                // An organization car needs a member; an unknown organization has none.
                if (request.organizationId != null && organizations.findMembership(request.organizationId, user.id) == null) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }
                // toCar rejects invalid or missing fields with an IllegalArgumentException.
                val car = runCatching {
                    request.toCar(ownerId = user.id.takeIf { request.organizationId == null }, organizationId = request.organizationId)
                }.getOrNull()
                if (car == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                try {
                    call.respond(HttpStatusCode.Created, message = repository.addCar(car))
                } catch (e: LicensePlateTakenException) {
                    call.respond(HttpStatusCode.Conflict)
                }
            }

            // Owner, or a member for an organization car.
            get("/cars/{id}/tco") {
                val id = call.parameters["id"]?.toIntOrNull()
                val car = id?.let(repository::findCar)
                when {
                    id == null -> call.respond(HttpStatusCode.BadRequest)
                    car == null -> call.respond(HttpStatusCode.NotFound)
                    !canManage(call.principal<User>()!!, car, organizations) -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        val raw = call.request.queryParameters["kmPerYear"]
                        val kmPerYear = if (raw == null) STANDARD_KM_PER_YEAR else raw.toIntOrNull()?.takeIf { it >= 0 }
                        if (kmPerYear == null) call.respond(HttpStatusCode.BadRequest)
                        else call.respond(TcoResponse(id, kmPerYear, car.tcoPerYear(kmPerYear, EnergyPrices.STANDARD).roundTo(2), "EUR"))
                    }
                }
            }

            put("/cars/{id}") {
                val id = call.parameters.getOrFail<Int>("id")
                val existing = repository.findCar(id)
                when {
                    existing == null -> call.respond(HttpStatusCode.NotFound)
                    !canManage(call.principal<User>()!!, existing, organizations) -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        val request = call.receive<AddCarRequest>()
                        // toCar rejects invalid fields with an IllegalArgumentException.
                        val car = runCatching { request.toCar(existing.ownerId, id, existing, existing.organizationId) }.getOrNull()
                        // A car can't change type or move to another owner or organization.
                        if (car == null || request.type != existing.type || (request.organizationId ?: existing.organizationId) != existing.organizationId) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@put
                        }
                        car.status = existing.status // the request has no status
                        // null when the car was deleted meanwhile
                        try {
                            repository.updateCar(car)?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
                        } catch (e: LicensePlateTakenException) {
                            call.respond(HttpStatusCode.Conflict)
                        }
                    }
                }
            }

            get("/cars/{id}") {
                val id = call.parameters["id"]

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID is missing")
                    return@get
                }

                // Check if ID is numeric value
                val carId = id.toIntOrNull()
                if (carId == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID must be of numeric value")
                    return@get
                }

                // Get car from database
                val car = repository.findCar(carId)
                if (car == null) {
                    call.respond(HttpStatusCode.NotFound, "Car with ID $id not found")
                    return@get
                }

                // Return JSON response, with the photo urls when images are set up.
                if (images == null) {
                    call.respond(car)
                } else {
                    val json = Json.encodeToJsonElement(Car.serializer(), car).jsonObject
                    call.respond(JsonObject(json + ("images" to JsonArray(images.forCar(carId).map { JsonPrimitive(it.url) }))))
                }
            }

            delete("/cars/{id}") {
                val user = call.principal<User>()!!
                val id = call.parameters["id"]

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID is missing")
                    return@delete
                }

                val carId = id.toIntOrNull()
                if (carId == null) {
                    call.respond(HttpStatusCode.BadRequest, "ID must be of numeric value")
                    return@delete
                }

                val car = repository.findCar(carId)
                val photos = images?.forCar(carId).orEmpty()
                when {
                    car == null -> call.respond(HttpStatusCode.NotFound, "Car with ID $id not found")
                    !canDelete(user, car, organizations) -> call.respond(HttpStatusCode.Forbidden)
                    // false when the car was deleted meanwhile
                    repository.deleteCarById(carId) -> {
                        images?.deleteFiles(photos)
                        call.respond(HttpStatusCode.NoContent)
                    }
                    else -> call.respond(HttpStatusCode.NotFound, "Car with ID $id not found")
                }
            }
        }
    }
}
