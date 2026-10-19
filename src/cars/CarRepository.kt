package com.example.cars

// Thrown when a car is saved with a license plate that another car has; the routes answer 409.
class LicensePlateTakenException : RuntimeException()

interface CarRepository {
    fun allCars(): List<Car>
    fun addCar(car: Car): Car
    fun getCarById(id: Int): Car?
}