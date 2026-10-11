package example.kronyka.presentation.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String? = null,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String? = null,

    @field:NotBlank(message = "Full name is required")
    @JsonProperty("full_name")
    @JsonAlias("fullName")
    val fullName: String? = null
)

data class RegisterResponse(
    @JsonProperty("user_id")
    val userId: UUID,
    val username: String,
    @JsonProperty("full_name")
    val fullName: String,
    val message: String = "User registered successfully"
)

data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String? = null,

    @field:NotBlank(message = "Password is required")
    val password: String? = null
)

data class UserSummaryDto(
    @JsonProperty("user_id")
    val userId: UUID,
    val username: String
)

data class LoginResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("token_type")
    val tokenType: String = "Bearer",
    @JsonProperty("expires_in")
    val expiresIn: Long = 86400,
    val user: UserSummaryDto
)

data class UserProfileResponse(
    @JsonProperty("user_id")
    val userId: UUID,
    val username: String,
    @JsonProperty("full_name")
    val fullName: String,
    @JsonProperty("created_at")
    val createdAt: LocalDateTime
)
