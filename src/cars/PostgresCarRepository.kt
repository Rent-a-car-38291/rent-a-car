package com.example.cars

import com.example.database.runScript
import java.sql.ResultSet
import javax.sql.DataSource

class PostgresCarRepository(private val dataSource: DataSource) : CarRepository {
    init {
        dataSource.runScript("cars.sql")
    }

    override fun allCars(): List<Car> = dataSource.connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM cars ORDER BY id").use { rows ->
                generateSequence { if (rows.next()) rows.toCar() else null }.toList()
            }
        }
    }

    private fun ResultSet.toCar(): Car {
        val id = getInt("id")
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
            "BEV" -> BEVCar(id, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption)
            "ICE" -> ICECar(id, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, FuelType.valueOf(getString("fuel_type")), capacity, consumption)
            "FCEV" -> FCEVCar(id, brand, licensePlate, mileage, status, rate, location, doors, bodyStyle, modelYear, power, transmission, color, capacity, consumption)
            else -> error("Unknown car type $type")
        }
    }
}
