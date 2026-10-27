package example.kronyka.infrastructure.persistence

import example.kronyka.infrastructure.persistence.tables.UserTable
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
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Repository
@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class ExposedUserRepository : UserRepository {

    override suspend fun findById(id: Uuid): User? = newSuspendedTransaction {
        UserTable.selectAll().where { UserTable.id eq id }
            .singleOrNull()
            ?.toDomain()
    }

    override suspend fun findByUsername(username: String): User? = newSuspendedTransaction {
        UserTable.selectAll().where { UserTable.username eq username }
            .singleOrNull()
            ?.toDomain()
    }

    override suspend fun existsByUsername(username: String): Boolean = newSuspendedTransaction {
        !UserTable.selectAll().where { UserTable.username eq username }.empty()
    }

    override suspend fun create(username: String, passwordHash: String, fullName: String): User = newSuspendedTransaction {
        val id = Uuid.generateV7()
        val now = Clock.System.now()
        UserTable.insert {
            it[UserTable.id] = id
            it[UserTable.username] = username
            it[UserTable.passwordHash] = passwordHash
            it[UserTable.fullName] = fullName
            it[UserTable.createdAt] = now
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
        UserTable.update({ UserTable.id eq user.id }) {
            it[UserTable.username] = user.username
            it[UserTable.passwordHash] = user.passwordHash
            it[UserTable.fullName] = user.fullName
        } > 0
    }

    override suspend fun delete(id: Uuid): Boolean = newSuspendedTransaction {
        UserTable.deleteWhere { UserTable.id eq id } > 0
    }

    private fun ResultRow.toDomain(): User = User(
        id = this[UserTable.id],
        username = this[UserTable.username],
        passwordHash = this[UserTable.passwordHash],
        fullName = this[UserTable.fullName],
        createdAt = this[UserTable.createdAt]
    )
}

