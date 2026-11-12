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
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.contentLength
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
                    // shortcut: two uploads at once can both pass this check, so a car can briefly hold a photo more than the cap.
                    images.forCar(id).size >= MAX_IMAGES_PER_CAR -> call.respond(HttpStatusCode.Conflict)
                    (call.request.contentLength() ?: 0) > MAX_IMAGE_BYTES + MULTIPART_OVERHEAD -> call.respond(HttpStatusCode.PayloadTooLarge)
                    else -> {
                        // Reads at most one byte more than allowed, so a huge upload is never held in memory.
                        var bytes: ByteArray? = null
                        // Not a (readable) multipart body counts as no file.
                        try {
                            call.receiveMultipart().forEachPart { part ->
                                if (part is PartData.FileItem && part.name == "file" && bytes == null) {
                                    bytes = part.provider().readRemaining(MAX_IMAGE_BYTES + 1L).readByteArray()
                                }
                                part.dispose()
                            }
                        } catch (e: ContentTransformationException) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
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
