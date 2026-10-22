package com.example.images

import com.example.cars.Cars
import com.example.cars.createCars
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource

private const val FOREIGN_KEY_VIOLATION = "23503"

// Only the url is kept in the database; the file lives in the directory under the same random name.
// Deleting the car takes its rows along.
private object Images : Table("images") {
    val id = integer("id").autoIncrement()
    val carId = integer("car_id").references(Cars.id, onDelete = ReferenceOption.CASCADE)
    val fileName = text("file_name").uniqueIndex("images_file_name_key")
    val url = text("url")
    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toImage() = Image(this[Images.id], this[Images.carId], this[Images.url])

class ImageRepository(dataSource: DataSource, private val directory: Path) {
    private val db = Database.connect(dataSource)

    init {
        db.createCars() // images reference cars
        transaction(db) { SchemaUtils.create(Images) }
        Files.createDirectories(directory)
    }

    // The file name is generated here, never taken from the client, so it can't point outside the directory.
    // null when the car was deleted meanwhile.
    fun add(carId: Int, type: ImageType, bytes: ByteArray): Image? {
        val fileName = "${UUID.randomUUID()}.${type.extension}"
        val file = directory.resolve(fileName)
        Files.write(file, bytes)
        return try {
            transaction(db) {
                Images.insert {
                    it[Images.carId] = carId
                    it[Images.fileName] = fileName
                    it[url] = "/images/$fileName"
                }.resultedValues!!.single().toImage()
            }
        } catch (e: ExposedSQLException) {
            Files.deleteIfExists(file)
            if (e.sqlState == FOREIGN_KEY_VIOLATION) null else throw e
        }
    }

    fun forCar(carId: Int): List<Image> = transaction(db) { Images.selectAll().where { Images.carId eq carId }.orderBy(Images.id).map { it.toImage() } }

    // Looked up by file name in the table, so a name that isn't ours (like ../x) finds nothing.
    fun file(fileName: String): Path? {
        val known = transaction(db) { !Images.selectAll().where { Images.fileName eq fileName }.empty() }
        return if (known) directory.resolve(fileName) else null
    }

    // Call after the car is deleted: the rows are gone with it, the files are not.
    // shortcut: files of a car removed by deleting its owner's account stay on disk, sweep them when disk space matters.
    fun deleteFiles(images: List<Image>) = images.forEach { Files.deleteIfExists(directory.resolve(it.url.substringAfterLast('/'))) }
}
