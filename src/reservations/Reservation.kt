package com.example.reservations

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

// The date is an ISO date (2026-10-12), kept as text in the JSON.
@Serializable
data class Reservation(val id: Int, val carId: Int, val userId: Int, val date: String, val daypart: Daypart)

@Serializable
data class ReserveRequest(val date: String, val daypart: String)

@Serializable
data class AvailabilityResponse(val carId: Int, val date: String, val freeDayparts: List<Daypart>)
