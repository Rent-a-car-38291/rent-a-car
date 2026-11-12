package com.example.cars

import com.example.database.TestDatabase
import com.example.organizations.OrganizationRepository
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

    @Test
    fun CostFieldsAreStoredAndSeededCarsHaveNone() {
        assertEquals(0.0, repository.allCars().first().purchasePrice)
        val seeded = repository.allCars().first { it is BEVCar }
        val request = AddCarRequest(
            CarType.BEV, "Costly", "COST-1", 1, 1.0, Location(1.0, 2.0), 4, seeded.bodyStyle, 2020, 1, seeded.transmission, "x",
            capacity = 1.0, consumption = 1.0, purchasePrice = 25_000.5, insurancePerYear = 600.0, roadTaxPerYear = 120.25, maintenancePerYear = 450.0,
        )

        val added = repository.addCar(request.toCar(seeded.ownerId))
        try {
            val found = assertNotNull(repository.findCar(added.id))
            assertEquals(25_000.5, found.purchasePrice)
            assertEquals(600.0, found.insurancePerYear)
            assertEquals(120.25, found.roadTaxPerYear)
            assertEquals(450.0, found.maintenancePerYear)
            assertEquals(0.0, assertNotNull(repository.updateCar(request.copy(purchasePrice = 0.0).toCar(seeded.ownerId, added.id))).purchasePrice)
        } finally {
            repository.deleteCarById(added.id)
        }
    }

    @Test
    fun ACarsTableWithoutTheCostColumnsIsUpgraded() {
        val db = org.jetbrains.exposed.v1.jdbc.Database.connect(TestDatabase.dataSource)
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(db) {
            exec("ALTER TABLE cars DROP COLUMN purchase_price, DROP COLUMN insurance_per_year, DROP COLUMN road_tax_per_year, DROP COLUMN maintenance_per_year")
        }

        val upgraded = PostgresCarRepository(TestDatabase.dataSource)

        assertTrue(upgraded.allCars().isNotEmpty())
        assertTrue(upgraded.allCars().all { it.purchasePrice == 0.0 && it.maintenancePerYear == 0.0 })
    }

    @Test
    fun ACarsTableWithoutTheLastServiceColumnIsUpgradedToTheCurrentMileage() {
        val db = org.jetbrains.exposed.v1.jdbc.Database.connect(TestDatabase.dataSource)
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(db) { exec("ALTER TABLE cars DROP COLUMN last_service_km") }

        val upgraded = PostgresCarRepository(TestDatabase.dataSource)

        assertTrue(upgraded.allCars().isNotEmpty())
        assertTrue(upgraded.allCars().all { it.lastServiceKm == it.mileage && !it.serviceDue })
    }

    @Test
    fun MarkServicedSetsTheLastServiceToTheMileageAndUnknownCarsAreNotFound() {
        val car = repository.allCars().first()
        org.jetbrains.exposed.v1.jdbc.transactions.transaction(org.jetbrains.exposed.v1.jdbc.Database.connect(TestDatabase.dataSource)) {
            exec("UPDATE cars SET last_service_km = 0 WHERE id = ${car.id}")
        }
        assertEquals(0, repository.findCar(car.id)!!.lastServiceKm)

        assertTrue(repository.markServiced(car.id))

        assertEquals(car.mileage, repository.findCar(car.id)!!.lastServiceKm)
        assertFalse(repository.markServiced(-12345))
    }

    @Test
    fun CarsLeftRentedByAnOlderVersionBecomeAvailable() {
        val id = repository.allCars().first { it.status == CarStatus.Available }.id
        TestDatabase.dataSource.connection.use { it.createStatement().execute("UPDATE cars SET status = 'Rented' WHERE id = $id") }

        val reopened = PostgresCarRepository(TestDatabase.dataSource)

        assertEquals(CarStatus.Available, assertNotNull(reopened.findCar(id)).status)
    }

    @Test
    fun ACarsTableWithoutOrganizationsIsUpgradedAndTakesOrganizationCars() {
        TestDatabase.dataSource.connection.use {
            it.createStatement().execute("ALTER TABLE cars DROP COLUMN organization_id")
            it.createStatement().execute("ALTER TABLE cars ALTER COLUMN owner_id SET NOT NULL")
        }

        val upgraded = PostgresCarRepository(TestDatabase.dataSource)

        val organization = OrganizationRepository(TestDatabase.dataSource).create("Upgrade Co", adminId = -1)
        val template = upgraded.allCars().first { it is BEVCar }
        val car = upgraded.addCar(
            AddCarRequest(CarType.BEV, "Org Motors", "UPG-1", 1, 1.0, Location(1.0, 2.0), 4, template.bodyStyle, 2020, 1, template.transmission, "x", capacity = 1.0, consumption = 1.0)
                .toCar(ownerId = null, organizationId = organization.id),
        )
        try {
            assertEquals(organization.id, assertNotNull(upgraded.findCar(car.id)).organizationId)
            assertNull(upgraded.findCar(car.id)!!.ownerId)
        } finally {
            upgraded.deleteCarById(car.id)
            OrganizationRepository(TestDatabase.dataSource).clear()
        }
    }

    @Test
    fun ACarHasAnOwnerOrAnOrganizationButNotBothOrNeither() {
        val template = repository.allCars().first { it is BEVCar }
        val request = AddCarRequest(CarType.BEV, "Odd Motors", "ODD-1", 1, 1.0, Location(1.0, 2.0), 4, template.bodyStyle, 2020, 1, template.transmission, "x", capacity = 1.0, consumption = 1.0)

        assertFails { repository.addCar(request.toCar(ownerId = null)) }
        assertFails { repository.addCar(request.toCar(ownerId = -1, organizationId = 1)) }
    }
}
