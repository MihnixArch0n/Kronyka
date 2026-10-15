package example.kronyka.infrastructure.persistence

import example.kronyka.data.table.Users
import example.kronyka.domain.model.User
import example.kronyka.domain.repository.UserRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class ExposedUserRepository : UserRepository {

    override suspend fun findById(id: UUID): User? = newSuspendedTransaction {
        Users.selectAll().where { Users.id eq id }
            .singleOrNull()
            ?.toDomain()
    }

    override suspend fun findByUsername(username: String): User? = newSuspendedTransaction {
        Users.selectAll().where { Users.username eq username }
            .singleOrNull()
            ?.toDomain()
    }

    override suspend fun existsByUsername(username: String): Boolean = newSuspendedTransaction {
        !Users.selectAll().where { Users.username eq username }.empty()
    }

    override suspend fun create(username: String, passwordHash: String, fullName: String): User = newSuspendedTransaction {
        val id = UUID.randomUUID()
        val now = LocalDateTime.now()
        Users.insert {
            it[Users.id] = id
            it[Users.username] = username
            it[Users.passwordHash] = passwordHash
            it[Users.fullName] = fullName
            it[Users.createdAt] = now
        }

        User(
            id = id,
            username = username,
            passwordHash = passwordHash,
            fullName = fullName,
            createdAt = now
        )
    }

    override suspend fun update(user: User): Boolean = newSuspendedTransaction {
        Users.update({ Users.id eq user.id }) {
            it[Users.username] = user.username
            it[Users.passwordHash] = user.passwordHash
            it[Users.fullName] = user.fullName
        } > 0
    }

    override suspend fun delete(id: UUID): Boolean = newSuspendedTransaction {
        Users.deleteWhere { Users.id eq id } > 0
    }

    private fun ResultRow.toDomain(): User = User(
        id = this[Users.id],
        username = this[Users.username],
        passwordHash = this[Users.passwordHash],
        fullName = this[Users.fullName],
        createdAt = this[Users.createdAt]
    )
}

