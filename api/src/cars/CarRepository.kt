package com.example.cars

/** Thrown when a car is saved with a license plate that another car has; the routes answer 409. */
class LicensePlateTakenException : RuntimeException()

/** Storage of the cars. A private car has an owner, an organization car an organization. */
interface CarRepository {
    /** All cars of every owner. */
    fun allCars(): List<Car>
    /** The car with this id, or null. */
    fun findCar(id: Int): Car?
    /** The cars that belong to the organization. */
    fun carsOfOrganization(organizationId: Int): List<Car> = allCars().filter { it.organizationId == organizationId }
    /** Saves a new car and returns it with its id. Throws LicensePlateTakenException when the plate is taken. */
    fun addCar(car: Car): Car
    /** Overwrites the editable fields of the car; null when it doesn't exist (anymore). Throws LicensePlateTakenException when the plate is taken. */
    fun updateCar(car: Car): Car?
    /** Sets the last service to the car's current mileage; false when the car doesn't exist (anymore). */
    fun markServiced(carId: Int): Boolean
    /** True when a car was deleted. */
    fun deleteCarById(carId: Int): Boolean
}
