package example.kronyka.presentation.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Schema(description = "User registration request payload")
@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class RegisterRequest(
    @field:Schema(description = "Unique username for authentication", example = "alice", minLength = 3, maxLength = 50)
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String? = null,

    @field:Schema(description = "Raw password (minimum 6 characters)", example = "SecurePassword123@", minLength = 6)
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String? = null,

    @field:Schema(description = "Display full name of the user", example = "Alice Wonderland")
    @field:NotBlank(message = "Full name is required")
    @JsonProperty("full_name")
    @JsonAlias("fullName")
    @SerialName("full_name")
    @JsonNames("fullName")
    val fullName: String? = null
)

@Schema(description = "User registration response payload")
@Serializable
data class RegisterResponse(
    @field:Schema(description = "Unique user identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @JsonProperty("user_id")
    @SerialName("user_id")
    val userId: Uuid,

    @field:Schema(description = "Registered username", example = "alice")
    val username: String,

    @field:Schema(description = "User's full name", example = "Alice Wonderland")
    @JsonProperty("full_name")
    @SerialName("full_name")
    val fullName: String
)

@Schema(description = "User login credentials payload")
@Serializable
data class LoginRequest(
    @field:Schema(description = "Registered username", example = "alice")
    @field:NotBlank(message = "Username is required")
    val username: String? = null,

    @field:Schema(description = "User password", example = "SecurePassword123@")
    @field:NotBlank(message = "Password is required")
    val password: String? = null
)

@Schema(description = "Summary representation of an authenticated user")
@Serializable
data class UserSummaryDto(
    @field:Schema(description = "Unique user identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @JsonProperty("user_id")
    @SerialName("user_id")
    val userId: Uuid,

    @field:Schema(description = "Username", example = "alice")
    val username: String
)

@Schema(description = "Login authentication response with Bearer JWT token")
@Serializable
data class LoginResponse(
    @field:Schema(description = "Stateless JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    @JsonProperty("access_token")
    @SerialName("access_token")
    val accessToken: String,

    @field:Schema(description = "Token authentication scheme", example = "Bearer")
    @JsonProperty("token_type")
    @SerialName("token_type")
    val tokenType: String = "Bearer",

    @field:Schema(description = "Token expiration duration in seconds", example = "86400")
    @JsonProperty("expires_in")
    @SerialName("expires_in")
    val expiresIn: Long = 86400,

    @field:Schema(description = "Authenticated user summary profile")
    val user: UserSummaryDto
)

@Schema(description = "Detailed user profile information")
@Serializable
data class UserProfileResponse(
    @field:Schema(description = "Unique user identifier", example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499")
    @JsonProperty("user_id")
    @SerialName("user_id")
    val userId: Uuid,

    @field:Schema(description = "Username", example = "alice")
    val username: String,

    @field:Schema(description = "Full name", example = "Alice Wonderland")
    @JsonProperty("full_name")
    @SerialName("full_name")
    val fullName: String,

    @field:Schema(description = "Account creation timestamp (ISO-8601)", example = "2026-10-01T08:00:00Z")
    @JsonProperty("created_at")
    @SerialName("created_at")
    val createdAt: Instant
)
