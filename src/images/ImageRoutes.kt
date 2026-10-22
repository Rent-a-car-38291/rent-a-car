package com.example.images

import com.example.accounts.User
import com.example.cars.CarRepository
import com.example.cars.canManage
import com.example.organizations.OrganizationRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.nio.file.Files

fun Application.configureImages(cars: CarRepository, organizations: OrganizationRepository, images: ImageRepository) {
    routing {
        // Public like the car list: the app shows the photos to renters who may not be logged in.
        get("/images/{name}") {
            val file = images.file(call.parameters["name"].orEmpty())
            val type = ImageType.entries.find { file?.fileName.toString().endsWith(".${it.extension}") }
            if (file == null || type == null || !Files.exists(file)) call.respond(HttpStatusCode.NotFound)
            else call.respondBytes(Files.readAllBytes(file), ContentType.parse(type.contentType))
        }

        authenticate {
            post("/cars/{id}/images") {
                val id = call.parameters["id"]?.toIntOrNull()
                val car = id?.let(cars::findCar)
                when {
                    id == null -> call.respond(HttpStatusCode.BadRequest)
                    car == null -> call.respond(HttpStatusCode.NotFound)
                    !canManage(call.principal<User>()!!, car, organizations) -> call.respond(HttpStatusCode.Forbidden)
                    else -> {
                        // Reads at most one byte more than allowed, so a huge upload is never held in memory.
                        var bytes: ByteArray? = null
                        call.receiveMultipart().forEachPart { part ->
                            if (part is PartData.FileItem && part.name == "file" && bytes == null) {
                                bytes = part.provider().readRemaining(MAX_IMAGE_BYTES + 1L).readByteArray()
                            }
                            part.dispose()
                        }
                        val type = bytes?.takeIf { it.size <= MAX_IMAGE_BYTES }?.let(::detectImageType)
                        when {
                            bytes == null -> call.respond(HttpStatusCode.BadRequest)
                            bytes!!.size > MAX_IMAGE_BYTES -> call.respond(HttpStatusCode.PayloadTooLarge)
                            type == null -> call.respond(HttpStatusCode.BadRequest)
                            else -> images.add(id, type, bytes!!)?.let { call.respond(HttpStatusCode.Created, it) } ?: call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }
            }
        }
    }
}
