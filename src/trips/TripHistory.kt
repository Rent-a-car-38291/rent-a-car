package com.example.trips

import com.example.reservations.Daypart
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable
import java.time.LocalDate

internal const val DEFAULT_TRIP_LIMIT = 20
internal const val MAX_TRIP_LIMIT = 100

// The date is an ISO date (2026-10-12) like on a reservation.
@Serializable
data class TripHistoryItem(val id: Int, val reservationId: Int, val carId: Int, val date: String, val daypart: Daypart, val distanceKm: Int, val score: Int, val startedAt: String?, val durationMinutes: Int?)

// from and to are inclusive dates of the reservation.
data class TripFilter(val from: LocalDate?, val to: LocalDate?, val limit: Int, val offset: Int)

// Failure when a value is invalid (not an ISO date, not a number, a limit outside 1..MAX_TRIP_LIMIT, a negative offset, or from after to).
fun parseTripFilter(query: Parameters): Result<TripFilter> = runCatching {
    val from = query["from"]?.let(LocalDate::parse)
    val to = query["to"]?.let(LocalDate::parse)
    val limit = query["limit"]?.toInt() ?: DEFAULT_TRIP_LIMIT
    val offset = query["offset"]?.toInt() ?: 0
    require(limit in 1..MAX_TRIP_LIMIT && offset >= 0 && (from == null || to == null || !from.isAfter(to)))
    TripFilter(from, to, limit, offset)
}
