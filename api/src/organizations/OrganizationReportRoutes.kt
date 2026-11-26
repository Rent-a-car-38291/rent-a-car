package com.example.organizations

import com.example.accounts.User
import com.example.cars.CarRepository
import com.example.trips.TripRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*

// Every member may read it: every member may manage the organization's cars.
fun Application.configureOrganizationReport(organizations: OrganizationRepository, cars: CarRepository, trips: TripRepository) {
    routing {
        authenticate {
            get("/organizations/{id}/report") {
                val id = call.parameters.getOrFail<Int>("id")
                val format = call.request.queryParameters["format"]
                when {
                    !organizations.exists(id) -> call.respond(HttpStatusCode.NotFound)
                    organizations.findMembership(id, call.principal<User>()!!.id) == null -> call.respond(HttpStatusCode.Forbidden)
                    format != null && format != "json" && format != "csv" -> call.respond(HttpStatusCode.BadRequest)
                    else -> {
                        val organizationCars = cars.carsOfOrganization(id)
                        val rows = buildReport(organizationCars, trips.usageByCar(organizationCars.map { it.id }))
                        if (format == "csv") call.respondText(reportCsv(rows), ContentType.Text.CSV.withCharset(Charsets.UTF_8)) else call.respond(rows)
                    }
                }
            }
        }
    }
}
