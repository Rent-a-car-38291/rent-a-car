package com.example.database

import com.example.cars.BodyStyle
import com.example.cars.Car
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private const val WIDTH = 800
private const val HEIGHT = 500

// The car colors in the seed data; anything else is drawn grey.
private val PAINT = mapOf(
    "black" to Color(0x2B2B2B), "white" to Color(0xF2F2EE), "blue" to Color(0x2F5DA8), "grey" to Color(0x8A8D90),
    "silver" to Color(0xC3C6C9), "red" to Color(0xC8372D), "green" to Color(0x3F7D4E), "yellow" to Color(0xE8C33A),
)

/**
 * A simple side view of [car] in its color, as a PNG: a picture for the demo data without image files of unclear origin.
 * Taller for an SUV, MPV or station wagon, lower for a cabrio or coupe.
 */
fun drawCar(car: Car): ByteArray {
    System.setProperty("java.awt.headless", "true") // no window or Dock icon for a server
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = Color(0xDEDBCF)
    g.fillRect(0, 0, WIDTH, HEIGHT)
    g.color = Color(0xC9C5B6)
    g.fillRect(0, 370, WIDTH, HEIGHT - 370) // the road

    val paint = PAINT[car.color.lowercase()] ?: PAINT.getValue("grey")
    val roof = when (car.bodyStyle) {
        BodyStyle.SUV, BodyStyle.MPV, BodyStyle.Stationwagon -> 150.0
        BodyStyle.Cabrio, BodyStyle.Coupe -> 205.0
        else -> 180.0
    }
    // The cabin: a slanted roof line over the body.
    val cabin = Path2D.Double().apply {
        moveTo(230.0, 260.0)
        lineTo(300.0, roof)
        lineTo(if (car.bodyStyle == BodyStyle.Stationwagon) 600.0 else 520.0, roof)
        lineTo(620.0, 260.0)
        closePath()
    }
    g.color = paint
    g.fill(cabin)
    g.fill(RoundRectangle2D.Double(120.0, 250.0, 560.0, 100.0, 60.0, 60.0))
    g.color = Color(0x26215C).let { Color(it.red, it.green, it.blue, 200) }
    g.fill(Path2D.Double().apply {
        moveTo(255.0, 255.0); lineTo(310.0, roof + 14); lineTo(510.0, roof + 14); lineTo(585.0, 255.0); closePath()
    })
    g.color = paint
    g.stroke = BasicStroke(10f)
    g.drawLine(420, roof.toInt() + 10, 420, 255) // the pillar between the windows

    for (x in listOf(250.0, 550.0)) {
        g.color = Color(0x161616)
        g.fill(Ellipse2D.Double(x - 50, 300.0, 100.0, 100.0))
        g.color = Color(0xB8B8B8)
        g.fill(Ellipse2D.Double(x - 22, 328.0, 44.0, 44.0))
    }
    g.dispose()
    return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
