package com.example.cars

// Thrown when a car is saved with a license plate that another car has; the routes answer 409.
class LicensePlateTakenException : RuntimeException()

interface CarRepository {
    fun allCars(): List<Car>
    fun findCar(id: Int): Car?
    fun carsOfOrganization(organizationId: Int): List<Car> = allCars().filter { it.organizationId == organizationId }
    fun addCar(car: Car): Car
    fun updateCar(car: Car): Car?
    fun markServiced(carId: Int): Boolean
    fun deleteCarById(carId: Int): Boolean
}
