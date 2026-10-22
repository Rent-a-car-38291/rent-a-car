package com.example.images

import kotlinx.serialization.Serializable

@Serializable
data class Image(val id: Int, val carId: Int, val url: String)

const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

enum class ImageType(val extension: String, val contentType: String) { JPEG("jpg", "image/jpeg"), PNG("png", "image/png") }

// The file's first bytes decide, not the type or the name the client claims.
fun detectImageType(bytes: ByteArray): ImageType? = when {
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> ImageType.JPEG
    bytes.size >= 8 && bytes.copyOf(8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> ImageType.PNG
    else -> null
}
