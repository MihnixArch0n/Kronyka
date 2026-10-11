package example.kronyka.data.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.datetime

object Users : Table("users") {

    val id = javaUUID("id")

    val username = varchar("username", 50).uniqueIndex()

    val passwordHash = varchar("password_hash", 100)

    val fullName = varchar("full_name", 100)

    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}