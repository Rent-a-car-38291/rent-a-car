package com.example.domain
import kotlinx.serialization.Serializable

@Serializable
class Location (
    var latitude: Double,
    var longitude: Double
)