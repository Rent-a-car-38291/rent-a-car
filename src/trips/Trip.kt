package com.example.trips

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.roundToInt

// The most one trip may report; keeps a typo from handing out a lifetime of points.
internal const val MAX_TRIP_KM = 10_000

@Serializable
data class Trip(val id: Int, val reservationId: Int, val distanceKm: Int, val score: Int, val startedAt: String?, val durationMinutes: Int?)

// startedAt is when the renter started the trip, an ISO instant; both are null for a trip sent in one call.
@Serializable
data class StartTripResponse(val reservationId: Int, val startedAt: String)

// The numbers are measured by the app; the score and the points are always calculated by the server.
@Serializable
data class TripRequest(val distanceKm: Int, val hardAccelerations: Int, val hardBrakes: Int)

@Serializable
data class TripResponse(val trip: Trip, val pointsAwarded: Int, val bonusPoints: Int)

// 100 for a calm drive, 5 less for every hard acceleration or brake per 100 km, never below 0.
// A trip of 0 km proves nothing, so it scores 0.
fun calculateScore(distanceKm: Int, hardAccelerations: Int, hardBrakes: Int): Int {
    if (distanceKm == 0) return 0
    val eventsPer100Km = (hardAccelerations.toLong() + hardBrakes) * 100.0 / distanceKm
    return (100 - 5 * eventsPer100Km).coerceIn(0.0, 100.0).roundToInt()
}

// One point per km at score 100, in proportion below that, rounded down.
fun bonusPoints(distanceKm: Int, score: Int): Int = distanceKm * score / 100

// Whole minutes, rounded up and at least 1: a trip that ended the moment it started still took a minute on the clock.
fun durationMinutes(startedAt: Instant, endedAt: Instant): Int =
    ceil(Duration.between(startedAt, endedAt).seconds / 60.0).toInt().coerceAtLeast(1)
