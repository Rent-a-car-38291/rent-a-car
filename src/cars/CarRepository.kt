package com.example.cars

interface CarRepository {
    fun allCars(): List<Car>
    fun findCar(id: Int): Car?
    fun addCar(car: Car): Car
    fun updateCar(car: Car): Car?
    fun deleteCarsOf(ownerId: Int)
}
