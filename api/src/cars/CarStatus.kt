package com.example.cars

// Whether a car is rented depends on the time, see Reservation: only Maintenance takes a car out of circulation.
enum class CarStatus {
    Available,
    Maintenance
}
