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

    @Test
    fun UpdateCarChangesTheFieldsAndFindCarReadsThemBack() {
        val original = repository.allCars().first()
        val changed = AddCarRequest(
            original.type, "Changed", original.licensePlate, 1, 9.99, Location(1.0, 2.0), original.doors, original.bodyStyle,
            original.modelYear, original.power, original.transmission, original.color,
            fuelType = FuelType.Diesel, capacity = 1.0, consumption = 2.0,
        ).toCar(original.ownerId, original.id).also { it.status = CarStatus.Maintenance }

        try {
            assertEquals("Changed", assertNotNull(repository.updateCar(changed)).brand)
            assertEquals(9.99, assertNotNull(repository.findCar(original.id)).rate)
            assertEquals(original.status, repository.findCar(original.id)?.status) // the status is not part of an update
        } finally {
            repository.updateCar(original)
        }
        assertEquals(original.brand, repository.findCar(original.id)?.brand)
    }

    @Test
    fun UpdateAndFindOfUnknownCarReturnNull() {
        val car = repository.allCars().first()

        assertNull(repository.findCar(-5))
        assertNull(repository.updateCar(AddCarRequest(
            car.type, "x", "x", 1, 1.0, Location(1.0, 2.0), 4, car.bodyStyle, 2020, 1, car.transmission, "x",
            fuelType = FuelType.Diesel, capacity = 1.0, consumption = 1.0,
        ).toCar(car.ownerId, -5)))
    }

    @Test
    fun AddingACarWithATakenLicensePlateThrows() {
        val seeded = repository.allCars().first { it is ICECar }
        val duplicate = AddCarRequest(
            CarType.ICE, "Copy", seeded.licensePlate, 1, 1.0, Location(1.0, 2.0), 4, seeded.bodyStyle, 2020, 1, seeded.transmission, "x",
            fuelType = FuelType.Petrol, capacity = 1.0, consumption = 1.0,
        ).toCar(seeded.ownerId)

        assertFailsWith<LicensePlateTakenException> { repository.addCar(duplicate) }
    }

    @Test
    fun UpdatingACarToATakenLicensePlateThrows() {
        val seededIce = repository.allCars().first { it is ICECar }
        val other = repository.allCars().first { it is ICECar && it.id != seededIce.id }
        val request = AddCarRequest(
            CarType.ICE, "Copy", "x", 1, 1.0, Location(1.0, 2.0), 4, seededIce.bodyStyle, 2020, 1, seededIce.transmission, "x",
            fuelType = FuelType.Petrol, capacity = 1.0, consumption = 1.0,
        )

        assertFailsWith<LicensePlateTakenException> {
            repository.updateCar(request.copy(licensePlate = other.licensePlate).toCar(seededIce.ownerId, seededIce.id))
        }
    }
}
