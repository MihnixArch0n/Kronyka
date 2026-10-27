package example.kronyka.domain.repository

import example.kronyka.domain.model.User
import kotlin.uuid.Uuid

interface UserRepository {
    suspend fun findById(id: Uuid): User?
    suspend fun findByUsername(username: String): User?
    suspend fun existsByUsername(username: String): Boolean
    suspend fun create(username: String, passwordHash: String, fullName: String): User
    suspend fun update(user: User): Boolean
    suspend fun delete(id: Uuid): Boolean
}