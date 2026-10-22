package example.kronyka.presentation.controller

import example.kronyka.domain.model.AppException
import example.kronyka.domain.repository.UserRepository

import example.kronyka.domain.service.AuthService
import example.kronyka.infrastructure.security.JwtTokenProvider
import example.kronyka.presentation.dto.*
import jakarta.validation.Valid
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userRepository: UserRepository
) {

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

    @GetMapping("/me")
    fun getCurrentUser(
        @AuthenticationPrincipal userId: UUID
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
