package example.kronyka.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class User(
    val id: Uuid,
    val username: String,
    val passwordHash: String,
    val fullName: String,
    val createdAt: Instant
)