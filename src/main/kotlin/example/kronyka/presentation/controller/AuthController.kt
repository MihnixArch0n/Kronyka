package example.kronyka.presentation.controller

import example.kronyka.domain.model.AppException
import example.kronyka.domain.repository.UserRepository

import example.kronyka.domain.service.AuthService
import example.kronyka.infrastructure.security.JwtTokenProvider
import example.kronyka.presentation.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import kotlin.uuid.Uuid

@Tag(name = "Authentication", description = "Endpoints for user registration, authentication, and profile lookup")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository
) {

    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account with a unique username and hashed password"
    )
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest
    ): ResponseEntity<ApiResponse<RegisterResponse>> = runBlocking {
        val user = authService.register(
            username = request.username!!,
            password = request.password!!,
            fullName = request.fullName!!
        )
        val response = RegisterResponse(
            userId = user.id,
            username = user.username,
            fullName = user.fullName
        )
        ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse.success(result = response)
        )
    }

    @Operation(
        summary = "User login",
        description = "Authenticates credentials and returns a Bearer JWT access token"
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> = runBlocking {
        val user = authService.login(
            username = request.username!!,
            password = request.password!!
        )
        val token = jwtTokenProvider.generateToken(user)
        val response = LoginResponse(
            accessToken = token,
            tokenType = "Bearer",
            expiresIn = jwtTokenProvider.validityInSeconds,
            user = UserSummaryDto(
                userId = user.id,
                username = user.username
            )
        )
        ResponseEntity.ok(
            ApiResponse.success(result = response)
        )
    }

    @Operation(
        summary = "Get current user profile",
        description = "Retrieves profile details of the authenticated user",
        security = [SecurityRequirement(name = "BearerAuth")]
    )
    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal userId: Uuid
    ): ResponseEntity<ApiResponse<UserProfileResponse>> = runBlocking {
        val user = userRepository.findById(userId)
            ?: throw AppException(ErrorCode.USER_NOT_FOUND, "User not found")


        val response = UserProfileResponse(
            userId = user.id,
            username = user.username,
            fullName = user.fullName,
            createdAt = user.createdAt
        )
        ResponseEntity.ok(
            ApiResponse.success(result = response)
        )
    }

}
