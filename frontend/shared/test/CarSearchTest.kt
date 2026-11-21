package com.avans.rentacar

import com.avans.rentacar.api.ApiJson
import com.avans.rentacar.cars.Car
import com.avans.rentacar.cars.CarKind
import com.avans.rentacar.cars.CarQuery
import com.avans.rentacar.cars.Daypart
import com.avans.rentacar.cars.Sort
import com.avans.rentacar.cars.summary
import com.avans.rentacar.search.countText
import com.avans.rentacar.search.dayLabel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CarSearchTest {
    private val today = LocalDate(2026, 11, 21) // a Saturday

    @Test
    fun `a plain search only sends sort and paging`() {
        assertEquals(listOf("sort" to "rate", "limit" to "20", "offset" to "0"), CarQuery().params(offset = 0, limit = 20))
    }

    @Test
    fun `a date sends its daypart too, and the filters use the API's names`() {
        val query = CarQuery(date = LocalDate(2026, 11, 22), daypart = Daypart.EVENING, kind = CarKind.Combustion, bodyStyle = "SUV", transmission = "Manual", fuelType = "Diesel", sort = Sort.Priciest)
        assertEquals(
            listOf("date" to "2026-11-22", "daypart" to "EVENING", "type" to "ICE", "bodyStyle" to "SUV", "transmission" to "Manual", "fuelType" to "Diesel", "sort" to "-rate", "limit" to "20", "offset" to "40"),
            query.params(offset = 40, limit = 20),
        )
    }

    @Test
    fun `only combustion cars keep a fuel type`() {
        val diesel = CarQuery().withKind(CarKind.Combustion).copy(fuelType = "Diesel")
        assertEquals("Diesel", diesel.withKind(CarKind.Combustion).fuelType)
        assertEquals(null, diesel.withKind(CarKind.Electric).fuelType)
        assertEquals(null, diesel.withKind(null).fuelType)
    }

    @Test
    fun `days read as today, tomorrow, then weekday and date`() {
        assertEquals("today", dayLabel(today, today))
        assertEquals("tomorrow", dayLabel(LocalDate(2026, 11, 22), today))
        assertEquals("mon 23", dayLabel(LocalDate(2026, 11, 23), today))
    }

    @Test
    fun `the count line says what is shown and when the cars are free`() {
        assertEquals("14 cars", countText(CarQuery(), today, shown = 14, total = 14))
        assertEquals("1 car", countText(CarQuery(), today, shown = 1, total = 1))
        assertEquals("showing 20 of 134 cars", countText(CarQuery(), today, shown = 20, total = 134))
        assertEquals("3 cars free today, morning", countText(CarQuery(date = today), today, shown = 3, total = 3))
        assertEquals("3 cars free on mon 23, evening", countText(CarQuery(date = LocalDate(2026, 11, 23), daypart = Daypart.EVENING), today, 3, 3))
    }

    @Test
    fun `a car from the API reads with the fields the app needs and ignores the rest`() {
        val json = """[{"type":"BEV","id":7,"ownerId":null,"brand":"Tesla Model 3","licensePlate":"AB-123-C","mileage":12000,
            "lastServiceKm":0,"status":"Available","rate":35.0,"location":{"latitude":51.5,"longitude":4.7},"doors":4,
            "bodyStyle":"Sedan","modelYear":2023,"power":208,"transmission":"Automatic","color":"white",
            "batteryCapacityKwh":60.0,"consumptionKwhPer100Km":14.0,"averageRating":null}]"""
        val car = ApiJson.decodeFromString<List<Car>>(json).single()
        assertEquals(Car(7, "BEV", "Tesla Model 3", 35.0, "Sedan", "Automatic"), car)
        assertEquals("electric · sedan · automatic", car.summary)
    }
}
