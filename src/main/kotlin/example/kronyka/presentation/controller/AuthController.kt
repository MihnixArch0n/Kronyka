package example.kronyka.presentation.controller

import example.kronyka.domain.model.AppException
import example.kronyka.domain.repository.UserRepository
import example.kronyka.domain.service.AuthService
import example.kronyka.infrastructure.security.JwtTokenProvider
import example.kronyka.presentation.dto.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "201",
                description = "User registered successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = RegisterResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Validation failed or malformed request payload",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "ValidationFailed",
                                summary = "Field validation errors (blank username or short password)",
                                value = OpenApiDocConstants.AUTH_400_VALIDATION_ERROR
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "409",
                description = "Username already exists",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "UserAlreadyExists",
                                summary = "Username already registered in the system",
                                value = OpenApiDocConstants.AUTH_409_USER_EXISTS
                            )
                        ]
                    )
                ]
            )
        ]
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
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "Login successful, Bearer JWT token issued",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = LoginResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "400",
                description = "Missing or blank login credentials",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "ValidationFailed",
                                summary = "Username or password is required",
                                value = OpenApiDocConstants.AUTH_400_VALIDATION_ERROR
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Invalid credentials",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "InvalidCredentials",
                                summary = "Incorrect username or password",
                                value = OpenApiDocConstants.AUTH_401_INVALID_CREDENTIALS
                            )
                        ]
                    )
                ]
            )
        ]
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
    @ApiResponses(
        value = [
            SwaggerApiResponse(
                responseCode = "200",
                description = "Profile retrieved successfully",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = UserProfileResponse::class))]
            ),
            SwaggerApiResponse(
                responseCode = "401",
                description = "Unauthenticated or invalid token",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "Unauthenticated",
                                summary = "Missing or invalid JWT Bearer token",
                                value = OpenApiDocConstants.AUTH_401_UNAUTHENTICATED
                            )
                        ]
                    )
                ]
            ),
            SwaggerApiResponse(
                responseCode = "404",
                description = "User not found",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiResponse::class),
                        examples = [
                            ExampleObject(
                                name = "UserNotFound",
                                summary = "Authenticated user no longer exists",
                                value = OpenApiDocConstants.AUTH_404_USER_NOT_FOUND
                            )
                        ]
                    )
                ]
            )
        ]
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
