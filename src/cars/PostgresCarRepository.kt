package com.example.cars

import com.example.accounts.Users
import com.example.accounts.createUsers
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import com.example.organizations.Organizations
import com.example.organizations.createOrganizations
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import javax.sql.DataSource

internal object Cars : Table("cars") {
    val id = integer("id").autoIncrement()
    // Exactly one of the two is set: a private car has an owner and goes with its account, an organization car has an organization and stays when a member's account is deleted.
    val ownerId = integer("owner_id").references(Users.id, onDelete = ReferenceOption.CASCADE, fkName = "cars_owner_id_fkey").nullable()
    val organizationId = integer("organization_id").references(Organizations.id, onDelete = ReferenceOption.CASCADE, fkName = "cars_organization_id_fkey").nullable()
    val type = text("type").check { it inList listOf("BEV", "ICE", "FCEV") }
    val brand = text("brand")
    val licensePlate = text("license_plate").uniqueIndex("cars_license_plate_key")
    val mileage = integer("mileage").check { it greaterEq 0 }
    val status = text("status")
    val rate = decimal("rate", 8, 2).check { it greaterEq BigDecimal.ZERO }
    val latitude = double("latitude")
    val longitude = double("longitude")
    val doors = integer("doors")
    val bodyStyle = text("body_style")
    val modelYear = integer("model_year")
    val power = integer("power")
    val transmission = text("transmission")
    val color = text("color")
    // Only for ICE cars.
    val fuelType = text("fuel_type").nullable()
    // Tank or battery size and consumption per 100 km: kWh for BEV, litres for ICE, kg for FCEV.
    val capacity = double("capacity")
    val consumption = double("consumption")
    val purchasePrice = money("purchase_price")
    val insurancePerYear = money("insurance_per_year")
    val roadTaxPerYear = money("road_tax_per_year")
    val maintenancePerYear = money("maintenance_per_year")
    val lastServiceKm = integer("last_service_km").check { it greaterEq 0 }
    override val primaryKey = PrimaryKey(id)

    private fun money(name: String) = decimal(name, 10, 2).default(BigDecimal.ZERO).check { it greaterEq BigDecimal.ZERO }

    init {
        check("cars_owner_or_organization") { ownerId.isNull() neq organizationId.isNull() }
        check { ((type eq "ICE") and fuelType.isNotNull()) or ((type neq "ICE") and fuelType.isNull()) }
    }
}

// Sample fleet, only inserted when the table is empty. The id is ignored: the database hands out ids.
private val SEED_CARS = listOf(
    ICECar(0, -1, "Peugeot", "85-GHB-2", 217349, CarStatus.Available, 25.0, Location(10.0, 29.294), 5, BodyStyle.Hatchback, 2008, 95, Transmission.Manual, "Black", FuelType.Petrol, 50.0, 6.10),
    BEVCar(0, -1, "Tesla Model 3", "K-123-BV", 48210, CarStatus.Available, 55.0, Location(52.3676, 4.9041), 4, BodyStyle.Sedan, 2021, 208, Transmission.Automatic, "White", 60.0, 14.5),
    FCEVCar(0, -1, "Toyota Mirai", "XT-482-H", 32500, CarStatus.Available, 70.0, Location(51.9244, 4.4777), 4, BodyStyle.Sedan, 2022, 134, Transmission.Automatic, "Blue", 5.6, 0.76),
    ICECar(0, -1, "Volkswagen Golf", "H-482-LK", 84200, CarStatus.Available, 35.0, Location(52.0907, 5.1214), 5, BodyStyle.Hatchback, 2019, 85, Transmission.Manual, "Grey", FuelType.Petrol, 50.0, 5.4),
    ICECar(0, -1, "Volvo V60", "KL-283-D", 126500, CarStatus.Available, 48.0, Location(51.4416, 5.4697), 5, BodyStyle.Stationwagon, 2018, 140, Transmission.Automatic, "Silver", FuelType.Diesel, 60.0, 4.9),
    ICECar(0, -1, "Renault Clio", "7-TNP-62", 61000, CarStatus.Available, 28.0, Location(51.5719, 4.7683), 5, BodyStyle.Hatchback, 2020, 67, Transmission.Manual, "Red", FuelType.Petrol, 42.0, 5.2),
    ICECar(0, -1, "Ford Focus", "ZV-117-P", 143000, CarStatus.Maintenance, 30.0, Location(53.2194, 6.5665), 5, BodyStyle.Hatchback, 2016, 92, Transmission.Manual, "Blue", FuelType.LPG, 52.0, 7.4),
    ICECar(0, -1, "Mazda MX-5", "R-905-ZB", 38900, CarStatus.Available, 60.0, Location(52.1601, 4.4970), 2, BodyStyle.Cabrio, 2021, 135, Transmission.Manual, "Red", FuelType.Petrol, 45.0, 6.8),
    BEVCar(0, -1, "Nissan Leaf", "N-641-GH", 72300, CarStatus.Available, 38.0, Location(52.0116, 4.3571), 5, BodyStyle.Hatchback, 2020, 110, Transmission.Automatic, "White", 40.0, 16.0),
    BEVCar(0, -1, "Renault Zoe", "G-208-RT", 55100, CarStatus.Available, 32.0, Location(52.2215, 6.8937), 5, BodyStyle.Hatchback, 2021, 100, Transmission.Automatic, "Blue", 52.0, 17.2),
    BEVCar(0, -1, "Hyundai Kona", "XB-370-F", 29800, CarStatus.Available, 52.0, Location(51.8126, 5.8372), 5, BodyStyle.SUV, 2023, 150, Transmission.Automatic, "Grey", 64.0, 14.7),
    FCEVCar(0, -1, "Hyundai Nexo", "TS-556-K", 21400, CarStatus.Available, 75.0, Location(52.0705, 4.3007), 5, BodyStyle.SUV, 2022, 120, Transmission.Automatic, "Silver", 6.3, 0.95),
    FCEVCar(0, -1, "Toyota Mirai", "VP-912-N", 18700, CarStatus.Available, 72.0, Location(52.3874, 4.6462), 4, BodyStyle.Sedan, 2023, 134, Transmission.Automatic, "Black", 5.6, 0.76),
)

// Also used by the reservations, which reference cars.
internal fun Database.createCars() {
    createOrganizations() // also creates the users, and cars reference both
    transaction(this) {
        SchemaUtils.create(Cars)
        // SchemaUtils.create leaves an older table alone, so replace the foreign key of a table the old SQL script made:
        // a deleted user takes the cars along. Same name as the declaration above, so a new table ends up with one.
        exec("ALTER TABLE cars DROP CONSTRAINT IF EXISTS cars_owner_id_fkey, ADD CONSTRAINT cars_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE")
        // Same for the columns added later.
        exec("ALTER TABLE cars ADD COLUMN IF NOT EXISTS purchase_price NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (purchase_price >= 0)")
        exec("ALTER TABLE cars ADD COLUMN IF NOT EXISTS insurance_per_year NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (insurance_per_year >= 0)")
        exec("ALTER TABLE cars ADD COLUMN IF NOT EXISTS road_tax_per_year NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (road_tax_per_year >= 0)")
        exec("ALTER TABLE cars ADD COLUMN IF NOT EXISTS maintenance_per_year NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (maintenance_per_year >= 0)")
        // A car from before service tracking counts as serviced at its current mileage.
        exec("ALTER TABLE cars ADD COLUMN IF NOT EXISTS last_service_km INTEGER CHECK (last_service_km >= 0)")
        exec("UPDATE cars SET last_service_km = mileage WHERE last_service_km IS NULL")
        exec("ALTER TABLE cars ALTER COLUMN last_service_km SET NOT NULL")
        // Organization cars: a car without an owner belongs to an organization.
        exec("ALTER TABLE cars ALTER COLUMN owner_id DROP NOT NULL")
        exec("ALTER TABLE cars ADD COLUMN IF NOT EXISTS organization_id INTEGER CONSTRAINT cars_organization_id_fkey REFERENCES organizations (id) ON DELETE CASCADE")
        exec("ALTER TABLE cars DROP CONSTRAINT IF EXISTS cars_owner_or_organization, ADD CONSTRAINT cars_owner_or_organization CHECK ((owner_id IS NULL) <> (organization_id IS NULL))")
        // Rented is gone (a car is rented per daypart, see Reservation): free the cars an older version left rented.
        exec("UPDATE cars SET status = 'Available' WHERE status = 'Rented'")
    }
}

class PostgresCarRepository(dataSource: DataSource) : CarRepository {
    private val db = Database.connect(dataSource)

    init {
        db.createCars()
        transaction(db) { if (Cars.selectAll().empty()) SEED_CARS.forEach(::insertCar) }
    }

    override fun allCars(): List<Car> = transaction(db) { Cars.selectAll().orderBy(Cars.id).map { it.toCar() } }

    // Translate the Kotlin subclass into the values for the type-specific columns.
    private fun typeColumns(car: Car) = when (car) {
        is BEVCar -> TypeColumns("BEV", null, car.batteryCapacityKwh, car.consumptionKwhPer100Km)
        is ICECar -> TypeColumns("ICE", car.fuelType.name, car.tankCapacityLitres, car.consumptionLitresPer100Km)
        is FCEVCar -> TypeColumns("FCEV", null, car.tankCapacityKg, car.consumptionKgPer100Km)
    }

    // null when the car doesn't exist (anymore). The owner, type, id and status of a car never change here,
    // so an update can't undo a change made meanwhile.
    override fun updateCar(car: Car): Car? {
        val columns = typeColumns(car)
        return try {
            transaction(db) {
                val updated = Cars.update({ Cars.id eq car.id }) {
                    it[brand] = car.brand
                    it[licensePlate] = car.licensePlate
                    it[mileage] = car.mileage
                    it[rate] = BigDecimal.valueOf(car.rate)
                    it[latitude] = car.location.latitude
                    it[longitude] = car.location.longitude
                    it[doors] = car.doors
                    it[bodyStyle] = car.bodyStyle.name
                    it[modelYear] = car.modelYear
                    it[power] = car.power
                    it[transmission] = car.transmission.name
                    it[color] = car.color
                    it[fuelType] = columns.fuelType
                    it[capacity] = columns.capacity
                    it[consumption] = columns.consumption
                    it[purchasePrice] = BigDecimal.valueOf(car.purchasePrice)
                    it[insurancePerYear] = BigDecimal.valueOf(car.insurancePerYear)
                    it[roadTaxPerYear] = BigDecimal.valueOf(car.roadTaxPerYear)
                    it[maintenancePerYear] = BigDecimal.valueOf(car.maintenancePerYear)
                    it[lastServiceKm] = car.lastServiceKm
                }
                if (updated > 0) findCar(car.id) else null
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState == "23505") throw LicensePlateTakenException() else throw e
        }
    }

    // Must run inside a transaction.
    private fun insertCar(car: Car): Car {
        val columns = typeColumns(car)
        return Cars.insert {
            it[type] = columns.type
            it[ownerId] = car.ownerId
            it[organizationId] = car.organizationId
            it[brand] = car.brand
            it[licensePlate] = car.licensePlate
            it[mileage] = car.mileage
            it[status] = car.status.name
            it[rate] = BigDecimal.valueOf(car.rate)
            it[latitude] = car.location.latitude
            it[longitude] = car.location.longitude
            it[doors] = car.doors
            it[bodyStyle] = car.bodyStyle.name
            it[modelYear] = car.modelYear
            it[power] = car.power
            it[transmission] = car.transmission.name
            it[color] = car.color
            it[fuelType] = columns.fuelType
            it[capacity] = columns.capacity
            it[consumption] = columns.consumption
            it[purchasePrice] = BigDecimal.valueOf(car.purchasePrice)
            it[insurancePerYear] = BigDecimal.valueOf(car.insurancePerYear)
            it[roadTaxPerYear] = BigDecimal.valueOf(car.roadTaxPerYear)
            it[maintenancePerYear] = BigDecimal.valueOf(car.maintenancePerYear)
            it[lastServiceKm] = car.lastServiceKm
        }.resultedValues!!.single().toCar()
    }

    // The unique index on license_plate decides, so two requests at once can't both win.
    override fun addCar(car: Car): Car = try {
        transaction(db) { insertCar(car) }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == "23505") throw LicensePlateTakenException() else throw e
    }

    private fun ResultRow.toCar(): Car {
        val id = this[Cars.id]
        val ownerId = this[Cars.ownerId]
        val organizationId = this[Cars.organizationId]
        val brand = this[Cars.brand]
        val licensePlate = this[Cars.licensePlate]
        val mileage = this[Cars.mileage]
        val status = CarStatus.valueOf(this[Cars.status])
        val rate = this[Cars.rate].toDouble()
        val location = Location(this[Cars.latitude], this[Cars.longitude])
        val doors = this[Cars.doors]
        val bodyStyle = BodyStyle.valueOf(this[Cars.bodyStyle])
        val modelYear = this[Cars.modelYear]
        val power = this[Cars.power]
        val transmission = Transmission.valueOf(this[Cars.transmission])
        val color = this[Cars.color]
        val capacity = this[Cars.capacity]
        val consumption = this[Cars.consumption]
        val purchasePrice = this[Cars.purchasePrice].toDouble()
        val insurancePerYear = this[Cars.insurancePerYear].toDouble()
        val roadTaxPerYear = this[Cars.roadTaxPerYear].toDouble()
        val maintenancePerYear = this[Cars.maintenancePerYear].toDouble()
        val lastServiceKm = this[Cars.lastServiceKm]
        return when (val type = this[Cars.type]) {
            "BEV" -> BEVCar(id, ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption, purchasePrice, insurancePerYear, roadTaxPerYear, maintenancePerYear, organizationId, lastServiceKm)
            "ICE" -> ICECar(id,ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, FuelType.valueOf(this[Cars.fuelType]!!), capacity, consumption, purchasePrice, insurancePerYear, roadTaxPerYear, maintenancePerYear, organizationId, lastServiceKm)
            "FCEV" -> FCEVCar(id, ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption, purchasePrice, insurancePerYear, roadTaxPerYear, maintenancePerYear, organizationId, lastServiceKm)
            else -> error("Unknown car type $type")
        }
    }

    override fun findCar(id: Int): Car? = transaction(db) { Cars.selectAll().where { Cars.id eq id }.singleOrNull()?.toCar() }

    // The new last service is the mileage in the same statement, so a trip ending at the same time can't be missed. False when the car doesn't exist (anymore).
    override fun markServiced(carId: Int): Boolean =
        transaction(db) { Cars.update({ Cars.id eq carId }) { it[lastServiceKm] = mileage } > 0 }

    override fun deleteCarById(carId: Int): Boolean = transaction(db) { Cars.deleteWhere { Cars.id eq carId } > 0 }
}

private data class TypeColumns(
    val type: String,
    val fuelType: String?,
    val capacity: Double,
    val consumption: Double,
)