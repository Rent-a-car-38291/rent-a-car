package com.example.cars
import kotlinx.serialization.Serializable

@Serializable
class Location (
    var latitude: Double,
    var longitude: Double
)