package com.avans.rentacar.api

import com.avans.rentacar.cars.Car
import com.avans.rentacar.defaultServerUrl
import com.avans.rentacar.cars.CarQuery
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PageSize = 20

/** The API sends more fields than the app reads. */
val ApiJson = Json { ignoreUnknownKeys = true }

/** A failed call, with a [message] to show as is. [unreachable] when the server did not answer at all. */
class ApiError(message: String, val unreachable: Boolean = false) : Exception(message)

/** One page of a list and how many there are in total (the API's X-Total-Count). */
data class Page<T>(val items: List<T>, val total: Int)

// Every error from the API has this body.
@Serializable
private class ErrorBody(val message: String)

/** Talks to the rent-a-car API. Every call throws [ApiError] when it fails, nothing else. */
class Api(var baseUrl: String = defaultServerUrl()) {
    private val client = HttpClient()

    suspend fun cars(query: CarQuery, offset: Int = 0, limit: Int = PageSize): Page<Car> {
        val response = send { client.get("$baseUrl/cars") { query.params(offset, limit).forEach { (k, v) -> parameter(k, v) } } }
        val cars = decode { ApiJson.decodeFromString<List<Car>>(response.bodyAsText()) }
        return Page(cars, response.headers["X-Total-Count"]?.toIntOrNull() ?: cars.size)
    }

    private suspend fun send(request: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception: on the web a failed fetch arrives as a JavaScript error.
            throw ApiError("Can't reach the server at $baseUrl. Check your connection and try again.", unreachable = true)
        }
        if (!response.status.isSuccess()) {
            val message = runCatching { ApiJson.decodeFromString<ErrorBody>(response.bodyAsText()).message }.getOrNull()
            throw ApiError(message ?: "Something went wrong (${response.status.value}). Try again.")
        }
        return response
    }

    private inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        throw ApiError("The server sent something the app doesn't understand. Try again later.")
    }
}
