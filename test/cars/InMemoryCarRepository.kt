package com.example.cars

class InMemoryCarRepository : CarRepository {
    val cars = mutableListOf<Car>()

    override fun allCars(): List<Car> = cars.toList()

    override fun findCar(id: Int): Car? = cars.find { it.id == id }

    override fun addCar(car: Car): Car = car.also(cars::add)

    override fun updateCar(car: Car): Car? =
        findCar(car.id)?.let { cars[cars.indexOf(it)] = car; car }
}
