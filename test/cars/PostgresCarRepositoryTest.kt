package com.example.cars

import com.example.database.TestDatabase
import kotlin.test.*

class PostgresCarRepositoryTest {
    private val repository = PostgresCarRepository(TestDatabase.dataSource)

    @Test
    fun SeedHasCarsOfAllThreeTypes() {
        val cars = repository.allCars()

        assertTrue(cars.size in 10..15)
        assertTrue(cars.any { it is ICECar })
        assertTrue(cars.any { it is BEVCar })
        assertTrue(cars.any { it is FCEVCar })
    }

    @Test
    fun EachTypeIsReadBackWithItsOwnFields() {
        val cars = repository.allCars().associateBy { it.licensePlate }

        val ice = cars.getValue("85-GHB-2") as ICECar
        assertEquals(FuelType.Petrol, ice.fuelType)
        assertEquals(50.0, ice.tankCapacityLitres)
        assertEquals(6.10, ice.consumptionLitresPer100Km)
        assertEquals(25.00, ice.rate)
        assertEquals(CarStatus.Available, ice.status)

        val bev = cars.getValue("K-123-BV") as BEVCar
        assertEquals(60.0, bev.batteryCapacityKwh)
        assertEquals(14.5, bev.consumptionKwhPer100Km)
        assertEquals(52.3676, bev.location.latitude)
        assertEquals(BodyStyle.Sedan, bev.bodyStyle)

        val fcev = cars.getValue("XT-482-H") as FCEVCar
        assertEquals(5.6, fcev.tankCapacityKg)
        assertEquals(0.76, fcev.consumptionKgPer100Km)
        assertEquals(Transmission.Automatic, fcev.transmission)
    }

    @Test
    fun StartingAgainDoesNotSeedTwice() {
        val before = repository.allCars().size

        PostgresCarRepository(TestDatabase.dataSource)

        assertEquals(before, repository.allCars().size)
    }
}
