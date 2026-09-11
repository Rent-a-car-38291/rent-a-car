package com.example.domain
import kotlinx.serialization.*

@Serializable
class Vehicle(val brand: String, val licensePlate: String, var mileage: Int) {

    fun updateMileage(amount: Int) {
        this.mileage += amount
    }
}