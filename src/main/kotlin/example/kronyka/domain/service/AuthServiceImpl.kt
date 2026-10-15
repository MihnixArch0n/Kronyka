package example.kronyka.domain.service

import example.kronyka.domain.model.AppException
import example.kronyka.domain.model.User
import example.kronyka.domain.repository.UserRepository
import example.kronyka.presentation.dto.ErrorCode
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : AuthService {

    override suspend fun register(username: String, password: String, fullName: String): User {
        val trimmedUsername = username.trim()
        val trimmedFullName = fullName.trim()

        require(trimmedUsername.isNotBlank()) { "Username cannot be blank" }
        require(password.isNotBlank()) { "Password cannot be blank" }
        require(trimmedFullName.isNotBlank()) { "Full name cannot be blank" }

        if (userRepository.existsByUsername(trimmedUsername)) {
            throw AppException(ErrorCode.USER_ALREADY_EXISTS, "Username '$trimmedUsername' is already taken")
        }

        val passwordHash = passwordEncoder.encode(password)
            ?: throw IllegalStateException("Password encoding failed")

        return userRepository.create(
            username = trimmedUsername,
            passwordHash = passwordHash,
            fullName = trimmedFullName
        )
    }

    override suspend fun login(username: String, password: String): User {
        val trimmedUsername = username.trim()

        require(trimmedUsername.isNotBlank()) { "Username cannot be blank" }
        require(password.isNotBlank()) { "Password cannot be blank" }

        val user = userRepository.findByUsername(trimmedUsername)
            ?: throw AppException(ErrorCode.INVALID_CREDENTIALS)

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw AppException(ErrorCode.INVALID_CREDENTIALS)
        }


        return user
    }
}
