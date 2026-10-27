package example.kronyka.infrastructure.persistence.tables

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object UserTable : Table("users") {
    val id = uuid("id")
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val fullName = varchar("full_name", 100)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}