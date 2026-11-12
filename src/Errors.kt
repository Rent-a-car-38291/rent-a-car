package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** The body of every error response: a short code that never changes and a text for people. */
@Serializable
data class ErrorResponse(val error: String, val message: String)

private val ERROR_STATUSES = arrayOf(
    HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.NotFound,
    HttpStatusCode.MethodNotAllowed, HttpStatusCode.Conflict, HttpStatusCode.PayloadTooLarge,
    HttpStatusCode.UnsupportedMediaType, HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError,
)

// "Bad Request" becomes bad_request.
internal fun HttpStatusCode.errorCode() = description.lowercase().replace(' ', '_')

internal fun HttpStatusCode.defaultMessage() = when (value) {
    400 -> "The request is not valid."
    401 -> "You are not logged in, or your login has expired."
    403 -> "You are not allowed to do this."
    404 -> "Not found."
    405 -> "This method is not allowed here."
    409 -> "The request conflicts with the current state."
    413 -> "The request is too large."
    415 -> "This content type is not supported."
    429 -> "Too many requests, try again later."
    else -> "Something went wrong on the server."
}

private val MessageKey = AttributeKey<String>("errorMessage")

/** Responds with an error status and a message about this request ("Car with ID 5 not found"); a bare respond(status) gets the default message. */
suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) {
    attributes.put(MessageKey, message)
    respond(status)
}

private fun Throwable.status() = when (this) {
    is BadRequestException -> HttpStatusCode.BadRequest
    is NotFoundException -> HttpStatusCode.NotFound
    is UnsupportedMediaTypeException, is CannotTransformContentToTypeException -> HttpStatusCode.UnsupportedMediaType
    is PayloadTooLargeException -> HttpStatusCode.PayloadTooLarge
    else -> HttpStatusCode.InternalServerError
}

/**
 * Gives every error status the same ErrorResponse body, also the ones Ktor sends itself
 * (unreadable bodies, unknown routes, a missing token), and turns an unhandled exception into a 400, 404, 413, 415 or 500.
 */
fun Application.configureErrors() {
    install(StatusPages) {
        status(*ERROR_STATUSES) { call, status ->
            // Headers set before, like the Bearer challenge of a missing token and Retry-After, stay on the response.
            call.respond(status, ErrorResponse(status.errorCode(), call.attributes.getOrNull(MessageKey) ?: status.defaultMessage()))
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            val status = cause.status()
            if (status == HttpStatusCode.InternalServerError) call.application.log.error("Unhandled exception on ${call.request.local.uri}", cause)
            // A response from an exception handler doesn't go through the status handler again, so it gets its body here.
            call.respond(status, ErrorResponse(status.errorCode(), status.defaultMessage()))
        }
    }
}
