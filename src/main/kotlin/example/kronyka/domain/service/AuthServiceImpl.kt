package example.kronyka.domain.service

import example.kronyka.domain.model.BadCredentialsException
import example.kronyka.domain.model.User
import example.kronyka.domain.model.UserAlreadyExistsException
import example.kronyka.domain.repository.UserRepository
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
            throw UserAlreadyExistsException("Username '$trimmedUsername' is already taken")
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
            ?: throw BadCredentialsException("Invalid username or password")

        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw BadCredentialsException("Invalid username or password")
        }

        return user
    }
}
