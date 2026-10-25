package com.example.reservations

import io.ktor.http.Parameters
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// A car is rented for one daypart of a date. Times are local time and only shown to the app, the server doesn't use them.
enum class Daypart(val from: LocalTime, val until: LocalTime) {
    MORNING(LocalTime.of(6, 0), LocalTime.of(12, 0)),
    AFTERNOON(LocalTime.of(12, 0), LocalTime.of(18, 0)),
    EVENING(LocalTime.of(18, 0), LocalTime.MAX),
}

// A daypart has started from its `from` time on; a started reservation can't be cancelled. `now` is local time.
fun hasStarted(date: LocalDate, daypart: Daypart, now: LocalDateTime): Boolean = !now.isBefore(date.atTime(daypart.from))

// RESERVED -> COMPLETED when the trip is recorded, RESERVED -> CANCELLED when the renter cancels. Both end states stay as they are.
enum class ReservationStatus {
    RESERVED, COMPLETED, CANCELLED;

    fun canMoveTo(next: ReservationStatus) = this == RESERVED && next != RESERVED
}

// The date is an ISO date (2026-10-12), kept as text in the JSON.
@Serializable
data class Reservation(val id: Int, val carId: Int, val userId: Int, val date: String, val daypart: Daypart, val status: ReservationStatus = ReservationStatus.RESERVED)

internal const val DEFAULT_RESERVATION_LIMIT = 20
internal const val MAX_RESERVATION_LIMIT = 100

data class ReservationFilter(val status: ReservationStatus?, val limit: Int, val offset: Int)

// Failure when the status is unknown, the limit is outside 1..MAX_RESERVATION_LIMIT, the offset is negative or a value is not a number.
fun parseReservationFilter(query: Parameters): Result<ReservationFilter> = runCatching {
    val status = query["status"]?.let { value -> ReservationStatus.entries.single { it.name == value } }
    val limit = query["limit"]?.toInt() ?: DEFAULT_RESERVATION_LIMIT
    val offset = query["offset"]?.toInt() ?: 0
    require(limit in 1..MAX_RESERVATION_LIMIT && offset >= 0)
    ReservationFilter(status, limit, offset)
}

@Serializable
data class ReserveRequest(val date: String, val daypart: String)

@Serializable
data class AvailabilityResponse(val carId: Int, val date: String, val freeDayparts: List<Daypart>)
