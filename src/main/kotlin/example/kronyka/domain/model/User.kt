package example.kronyka.domain.model

import java.time.LocalDateTime
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val passwordHash: String,
    val fullName: String,
    val createdAt: LocalDateTime
)