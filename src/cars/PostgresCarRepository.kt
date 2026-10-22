package com.example.cars

import com.example.database.runScript
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

private const val INSERT_CAR = """
    INSERT INTO cars (type, owner_id, brand, license_plate, mileage, status, rate,
        latitude, longitude, doors, body_style, model_year, power, transmission,
        color, fuel_type, capacity, consumption)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    RETURNING *
"""

class PostgresCarRepository(private val dataSource: DataSource) : CarRepository {
    init {
        dataSource.runScript("users.sql")
        dataSource.runScript("cars.sql")
    }

    override fun allCars(): List<Car> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM cars ORDER BY id").use { rows ->
                generateSequence { if (rows.next()) rows.toCar() else null }.toList()
            }
        }
    }

    override fun addCar(car: Car): Car {
        // Translate the Kotlin subclass into the values for the type-specific columns.
        val columns = when (car) {
            is BEVCar -> TypeColumns("BEV", null, car.batteryCapacityKwh, car.consumptionKwhPer100Km)
            is ICECar -> TypeColumns("ICE", car.fuelType.name, car.tankCapacityLitres, car.consumptionLitresPer100Km)
            is FCEVCar -> TypeColumns("FCEV", null, car.tankCapacityKg, car.consumptionKgPer100Km)
        }
        return queryCarOrConflict(
            INSERT_CAR,
            columns.type, car.ownerId, car.brand, car.licensePlate, car.mileage,
            car.status.name, car.rate, car.location.latitude, car.location.longitude,
            car.doors, car.bodyStyle.name, car.modelYear, car.power,
            car.transmission.name, car.color,
            columns.fuelType, columns.capacity, columns.consumption,
        ) ?: error("Insert returned no row")
    }

    private fun ResultSet.toCar(): Car {
        val id = getInt("id")
        val ownerId = getInt("owner_id")
        val brand = getString("brand")
        val licensePlate = getString("license_plate")
        val mileage = getInt("mileage")
        val status = CarStatus.valueOf(getString("status"))
        val rate = getDouble("rate")
        val location = Location(getDouble("latitude"), getDouble("longitude"))
        val doors = getInt("doors")
        val bodyStyle = BodyStyle.valueOf(getString("body_style"))
        val modelYear = getInt("model_year")
        val power = getInt("power")
        val transmission = Transmission.valueOf(getString("transmission"))
        val color = getString("color")
        val capacity = getDouble("capacity")
        val consumption = getDouble("consumption")
        return when (val type = getString("type")) {
            "BEV" -> BEVCar(id, ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption)
            "ICE" -> ICECar(id,ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, FuelType.valueOf(getString("fuel_type")), capacity, consumption)
            "FCEV" -> FCEVCar(id, ownerId, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption)
            else -> error("Unknown car type $type")
        }
    }

    // The unique index on license_plate decides, so two requests at once can't both win.
    private fun queryCarOrConflict(sql: String, vararg parameters: Any?): Car? = try {
        queryCar(sql, *parameters)
    } catch (e: SQLException) {
        if (e.sqlState == "23505") throw LicensePlateTakenException() else throw e
    }

    private fun queryCar(sql: String, vararg parameters: Any?): Car? = dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, parameter -> statement.setObject(index + 1, parameter) }
            statement.executeQuery().use { rows -> if (rows.next()) rows.toCar() else null }
        }
    }

    override fun getCarById(id: Int): Car? {
        val car = queryCar("SELECT * FROM cars WHERE id = ?", id)
        return car
    }

    override fun deleteCarById(carId: Int, ownerId: Int): Boolean {
        val rowsAffected = dataSource.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM cars WHERE id = ? AND owner_id = ?"
            ).use { statement ->
                statement.setInt(1, carId)
                statement.setInt(2, ownerId)
                statement.executeUpdate()
            }
        }
        return rowsAffected > 0
    }
}

private data class TypeColumns(
    val type: String,
    val fuelType: String?,
    val capacity: Double,
    val consumption: Double,
)