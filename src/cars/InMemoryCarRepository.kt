package com.example.cars

class InMemoryCarRepository : CarRepository {
    private val cars: List<Car> = listOf(
        ICECar(1, "Peugeot", "85-GHB-2",
        217349, CarStatus.Available, 25.00, Location(10.00, 29.294),
        5, BodyStyle.Hatchback, 2008, 95, Transmission.Manual, "Black",
        FuelType.Petrol, 50.0, 6.10),
        BEVCar(
            2, "Tesla Model 3", "K-123-BV",
            48210, CarStatus.Available, 55.00, Location(52.3676, 4.9041),
            4, BodyStyle.Sedan, 2021, 208, Transmission.Automatic, "White",
            60.0, 14.5
        ),
        FCEVCar(
            3, "Toyota Mirai", "XT-482-H",
            32500, CarStatus.Available, 70.00, Location(51.9244, 4.4777),
            4, BodyStyle.Sedan, 2022, 134, Transmission.Automatic, "Blue",
            5.6, 0.76
        )
    )

    override fun allCars(): List<Car> = cars
}