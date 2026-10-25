package com.example.reservations

import com.example.parsePaging
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A car is rented for one daypart of a date. Times are local time and only shown to the app, the server doesn't use them. */
enum class Daypart(val from: LocalTime, val until: LocalTime) {
    MORNING(LocalTime.of(6, 0), LocalTime.of(12, 0)),
    AFTERNOON(LocalTime.of(12, 0), LocalTime.of(18, 0)),
    EVENING(LocalTime.of(18, 0), LocalTime.MAX),
}

/** A daypart has started from its `from` time on; a started reservation can't be cancelled. `now` is local time. */
fun hasStarted(date: LocalDate, daypart: Daypart, now: LocalDateTime): Boolean = !now.isBefore(date.atTime(daypart.from))

/** RESERVED -> COMPLETED when the trip is recorded, RESERVED -> CANCELLED when the renter cancels. Both end states stay as they are. */
enum class ReservationStatus {
    RESERVED, COMPLETED, CANCELLED;

    fun canMoveTo(next: ReservationStatus) = this == RESERVED && next != RESERVED
}

// The date is an ISO date (2026-10-12), kept as text in the JSON.
@Serializable
data class Reservation(val id: Int, val carId: Int, val userId: Int, val date: String, val daypart: Daypart, val status: ReservationStatus = ReservationStatus.RESERVED)

data class ReservationFilter(val status: ReservationStatus?, val limit: Int, val offset: Int)

// Failure when the status is unknown or the limit or offset is outside the paging rules.
fun parseReservationFilter(query: Parameters): Result<ReservationFilter> = runCatching {
    val status = query["status"]?.let { value -> ReservationStatus.entries.single { it.name == value } }
    val paging = requireNotNull(parsePaging(query))
    ReservationFilter(status, paging.limit, paging.offset)
}

@Serializable
data class ReserveRequest(val date: String, val daypart: String)

@Serializable
data class AvailabilityResponse(val carId: Int, val date: String, val freeDayparts: List<Daypart>)
