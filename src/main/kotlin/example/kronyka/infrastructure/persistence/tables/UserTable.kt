package example.kronyka.infrastructure.persistence.tables

import org.jetbrains.exposed.v1.core.Table

object UserTable : Table() {
    val id = uuid("id")

    override val primaryKey = PrimaryKey(id)
}