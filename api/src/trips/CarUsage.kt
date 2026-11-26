package com.example.trips

// What the reservations and finished trips of one car add up to; the scores are summed so an average stays exact.
data class CarUsage(val reservations: Int, val trips: Int, val km: Long, val scoreSum: Long)
