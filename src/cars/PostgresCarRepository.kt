package com.example.cars

import com.example.accounts.Users
import com.example.accounts.createUsers
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
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import javax.sql.DataSource

private object Cars : Table("cars") {
    val id = integer("id").autoIncrement()
    val ownerId = integer("owner_id").references(Users.id)
    val type = text("type").check { it inList listOf("BEV", "ICE", "FCEV") }
    val brand = text("brand")
    val licensePlate = text("license_plate").uniqueIndex()
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
    override val primaryKey = PrimaryKey(id)

    init {
        check { ((type eq "ICE") and fuelType.isNotNull()) or ((type neq "ICE") and fuelType.isNull()) }
    }
}

// Sample fleet, only inserted when the table is empty.
// type, brand, plate, mileage, status, rate, lat, lon, doors, body style, year, power, transmission, color, fuel type, capacity, consumption
private val SEED_CARS = listOf(
    listOf("ICE", "Peugeot", "85-GHB-2", 217349, "Available", "25.00", 10.0, 29.294, 5, "Hatchback", 2008, 95, "Manual", "Black", "Petrol", 50.0, 6.10),
    listOf("BEV", "Tesla Model 3", "K-123-BV", 48210, "Available", "55.00", 52.3676, 4.9041, 4, "Sedan", 2021, 208, "Automatic", "White", null, 60.0, 14.5),
    listOf("FCEV", "Toyota Mirai", "XT-482-H", 32500, "Available", "70.00", 51.9244, 4.4777, 4, "Sedan", 2022, 134, "Automatic", "Blue", null, 5.6, 0.76),
    listOf("ICE", "Volkswagen Golf", "H-482-LK", 84200, "Available", "35.00", 52.0907, 5.1214, 5, "Hatchback", 2019, 85, "Manual", "Grey", "Petrol", 50.0, 5.4),
    listOf("ICE", "Volvo V60", "KL-283-D", 126500, "Rented", "48.00", 51.4416, 5.4697, 5, "Stationwagon", 2018, 140, "Automatic", "Silver", "Diesel", 60.0, 4.9),
    listOf("ICE", "Renault Clio", "7-TNP-62", 61000, "Available", "28.00", 51.5719, 4.7683, 5, "Hatchback", 2020, 67, "Manual", "Red", "Petrol", 42.0, 5.2),
    listOf("ICE", "Ford Focus", "ZV-117-P", 143000, "Maintenance", "30.00", 53.2194, 6.5665, 5, "Hatchback", 2016, 92, "Manual", "Blue", "LPG", 52.0, 7.4),
    listOf("ICE", "Mazda MX-5", "R-905-ZB", 38900, "Available", "60.00", 52.1601, 4.4970, 2, "Cabrio", 2021, 135, "Manual", "Red", "Petrol", 45.0, 6.8),
    listOf("BEV", "Nissan Leaf", "N-641-GH", 72300, "Available", "38.00", 52.0116, 4.3571, 5, "Hatchback", 2020, 110, "Automatic", "White", null, 40.0, 16.0),
    listOf("BEV", "Renault Zoe", "G-208-RT", 55100, "Rented", "32.00", 52.2215, 6.8937, 5, "Hatchback", 2021, 100, "Automatic", "Blue", null, 52.0, 17.2),
    listOf("BEV", "Hyundai Kona", "XB-370-F", 29800, "Available", "52.00", 51.8126, 5.8372, 5, "SUV", 2023, 150, "Automatic", "Grey", null, 64.0, 14.7),
    listOf("FCEV", "Hyundai Nexo", "TS-556-K", 21400, "Available", "75.00", 52.0705, 4.3007, 5, "SUV", 2022, 120, "Automatic", "Silver", null, 6.3, 0.95),
    listOf("FCEV", "Toyota Mirai", "VP-912-N", 18700, "Available", "72.00", 52.3874, 4.6462, 4, "Sedan", 2023, 134, "Automatic", "Black", null, 5.6, 0.76),
)

class PostgresCarRepository(dataSource: DataSource) : CarRepository {
    private val db = Database.connect(dataSource)

    init {
        db.createUsers()
        transaction(db) {
            SchemaUtils.create(Cars)
            if (Cars.selectAll().empty()) {
                Cars.batchInsert(SEED_CARS) { row ->
                    this[Cars.ownerId] = -1
                    this[Cars.type] = row[0] as String
                    this[Cars.brand] = row[1] as String
                    this[Cars.licensePlate] = row[2] as String
                    this[Cars.mileage] = row[3] as Int
                    this[Cars.status] = row[4] as String
                    this[Cars.rate] = BigDecimal(row[5] as String)
                    this[Cars.latitude] = row[6] as Double
                    this[Cars.longitude] = row[7] as Double
                    this[Cars.doors] = row[8] as Int
                    this[Cars.bodyStyle] = row[9] as String
                    this[Cars.modelYear] = row[10] as Int
                    this[Cars.power] = row[11] as Int
                    this[Cars.transmission] = row[12] as String
                    this[Cars.color] = row[13] as String
                    this[Cars.fuelType] = row[14] as String?
                    this[Cars.capacity] = row[15] as Double
                    this[Cars.consumption] = row[16] as Double
                }
            }
        }
    }

    override fun allCars(): List<Car> = transaction(db) { Cars.selectAll().orderBy(Cars.id).map { it.toCar() } }

    override fun addCar(car: Car): Car {
        // Translate the Kotlin subclass into the values for the type-specific columns.
        val columns = when (car) {
            is BEVCar -> TypeColumns("BEV", null, car.batteryCapacityKwh, car.consumptionKwhPer100Km)
            is ICECar -> TypeColumns("ICE", car.fuelType.name, car.tankCapacityLitres, car.consumptionLitresPer100Km)
            is FCEVCar -> TypeColumns("FCEV", null, car.tankCapacityKg, car.consumptionKgPer100Km)
        }
        // The unique index on license_plate decides, so two requests at once can't both win.
        return try {
            transaction(db) {
                Cars.insert {
                    it[type] = columns.type
                    it[ownerId] = car.ownerId
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
                }.resultedValues!!.single().toCar()
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState == "23505") throw LicensePlateTakenException() else throw e
        }
    }

    private fun ResultRow.toCar(): Car {
        val id = this[Cars.id]
        val ownerId = this[Cars.ownerId]
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
        return when (val type = this[Cars.type]) {
            "BEV" -> BEVCar(id, ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption)
            "ICE" -> ICECar(id,ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, FuelType.valueOf(this[Cars.fuelType]!!), capacity, consumption)
            "FCEV" -> FCEVCar(id, ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption)
            else -> error("Unknown car type $type")
        }
    }

    override fun getCarById(id: Int): Car? = transaction(db) { Cars.selectAll().where { Cars.id eq id }.singleOrNull()?.toCar() }

    override fun deleteCarById(carId: Int, ownerId: Int): Boolean =
        transaction(db) { Cars.deleteWhere { (Cars.id eq carId) and (Cars.ownerId eq ownerId) } > 0 }
}

private data class TypeColumns(
    val type: String,
    val fuelType: String?,
    val capacity: Double,
    val consumption: Double,
)