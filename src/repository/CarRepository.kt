package com.example.repository
import com.example.domain.Car

interface CarRepository {
    fun allCars(): List<Car>
}