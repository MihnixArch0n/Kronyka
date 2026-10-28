package example.kronyka.presentation.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
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
    @SerialName("full_name")
    @JsonNames("fullName")
    val fullName: String? = null
)

@Serializable
data class RegisterResponse(
    @JsonProperty("user_id")
    @SerialName("user_id")
    val userId: Uuid,
    val username: String,
    @JsonProperty("full_name")
    @SerialName("full_name")
    val fullName: String
)


@Serializable
data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String? = null,

    @field:NotBlank(message = "Password is required")
    val password: String? = null
)

@Serializable
data class UserSummaryDto(
    @JsonProperty("user_id")
    @SerialName("user_id")
    val userId: Uuid,
    val username: String
)

@Serializable
data class LoginResponse(
    @JsonProperty("access_token")
    @SerialName("access_token")
    val accessToken: String,
    @JsonProperty("token_type")
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @JsonProperty("expires_in")
    @SerialName("expires_in")
    val expiresIn: Long = 86400,
    val user: UserSummaryDto
)

@Serializable
data class UserProfileResponse(
    @JsonProperty("user_id")
    @SerialName("user_id")
    val userId: Uuid,
    val username: String,
    @JsonProperty("full_name")
    @SerialName("full_name")
    val fullName: String,
    @JsonProperty("created_at")
    @SerialName("created_at")
    val createdAt: Instant
)
