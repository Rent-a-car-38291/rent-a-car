package com.example.cars

class InMemoryCarRepository : CarRepository {
    val cars = mutableListOf<Car>()

    override fun allCars(): List<Car> = cars.toList()

    override fun findCar(id: Int): Car? = cars.find { it.id == id }

    override fun addCar(car: Car): Car {
        if (cars.any { it.licensePlate == car.licensePlate }) throw LicensePlateTakenException()
        return car.also(cars::add)
    }

    override fun updateCar(car: Car): Car? {
        if (cars.any { it.id != car.id && it.licensePlate == car.licensePlate }) throw LicensePlateTakenException()
        return findCar(car.id)?.let { cars[cars.indexOf(it)] = car; car }
    }

    override fun markServiced(carId: Int): Boolean {
        val car = findCar(carId) ?: return false
        car.lastServiceKm = car.mileage
        return true
    }

    override fun deleteCarById(carId: Int): Boolean = cars.removeIf { it.id == carId }
}
