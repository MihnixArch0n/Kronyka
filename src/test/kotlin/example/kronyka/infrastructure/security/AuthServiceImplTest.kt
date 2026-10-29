package example.kronyka.infrastructure.security

import example.kronyka.domain.model.AppException
import example.kronyka.domain.model.User
import example.kronyka.domain.repository.UserRepository
import example.kronyka.domain.service.AuthService
import example.kronyka.presentation.dto.ErrorCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class AuthServiceImplTest {

    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakePasswordEncoder: FakePasswordEncoder
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        fakeUserRepository = FakeUserRepository()
        fakePasswordEncoder = FakePasswordEncoder()
        authService = AuthServiceImpl(fakeUserRepository, fakePasswordEncoder)
    }

    @Test
    fun `register successfully creates new user with hashed password`() = runBlocking {
        val user = authService.register(
            username = "alice",
            password = "SecretPassword123@",
            fullName = "Alice Wonderland"
        )

        assertNotNull(user)
        assertNotNull(user.id)
        assertEquals("alice", user.username)
        assertEquals("Alice Wonderland", user.fullName)
        assertEquals("hashed_SecretPassword123@", user.passwordHash)
        assertEquals(user, fakeUserRepository.findByUsername("alice"))
    }

    @Test
    fun `register throws AppException when username is taken`() = runBlocking {
        authService.register(
            username = "alice",
            password = "Password123@",
            fullName = "Alice First"
        )

        val exception = assertThrows<AppException> {
            runBlocking {
                authService.register(
                    username = "alice",
                    password = "AnotherPassword456@",
                    fullName = "Alice Second"
                )
            }
        }

        assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.errorCode)
        assertTrue(exception.message!!.contains("alice"))
    }

    @Test
    fun `register throws IllegalArgumentException when fields are blank`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { authService.register("   ", "password", "Name") }
        }
        assertThrows<IllegalArgumentException> {
            runBlocking { authService.register("alice", "   ", "Name") }
        }
        assertThrows<IllegalArgumentException> {
            runBlocking { authService.register("alice", "password", "   ") }
        }
    }

    @Test
    fun `login successfully returns user when credentials match`() = runBlocking {
        val registeredUser = authService.register(
            username = "bob",
            password = "MyPassword123@",
            fullName = "Bob Builder"
        )

        val loggedInUser = authService.login(
            username = "bob",
            password = "MyPassword123@"
        )

        assertEquals(registeredUser, loggedInUser)
    }

    @Test
    fun `login throws AppException when user not found`() {
        val exception = assertThrows<AppException> {
            runBlocking {
                authService.login(
                    username = "non_existent_user",
                    password = "SomePassword123@"
                )
            }
        }
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.errorCode)
        assertEquals("Invalid username or password", exception.message)
    }

    @Test
    fun `login throws AppException when password does not match`() = runBlocking {
        authService.register(
            username = "charlie",
            password = "CorrectPassword123@",
            fullName = "Charlie Brown"
        )

        val exception = assertThrows<AppException> {
            runBlocking {
                authService.login(
                    username = "charlie",
                    password = "WrongPassword999@"
                )
            }
        }
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.errorCode)
        assertEquals("Invalid username or password", exception.message)
    }

    @Test
    fun `login throws IllegalArgumentException when credentials are blank`() {
        assertThrows<IllegalArgumentException> {
            runBlocking { authService.login("   ", "password") }
        }
        assertThrows<IllegalArgumentException> {
            runBlocking { authService.login("username", "   ") }
        }
    }

    private class FakePasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?): String = "hashed_$rawPassword"

        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean =
            encodedPassword == "hashed_$rawPassword"
    }

    private class FakeUserRepository : UserRepository {
        private val users = mutableListOf<User>()

        override suspend fun findById(id: Uuid): User? = users.find { it.id == id }

        override suspend fun findByUsername(username: String): User? =
            users.find { it.username.equals(username, ignoreCase = true) }

        override suspend fun existsByUsername(username: String): Boolean =
            users.any { it.username.equals(username, ignoreCase = true) }

        override suspend fun create(username: String, passwordHash: String, fullName: String): User {
            val user = User(
                id = Uuid.generateV7(),
                username = username,
                passwordHash = passwordHash,
                fullName = fullName,
                createdAt = Clock.System.now()
            )
            users.add(user)
            return user
        }

        override suspend fun update(user: User): Boolean {
            val index = users.indexOfFirst { it.id == user.id }
            return if (index != -1) {
                users[index] = user
                true
            } else false
        }

        override suspend fun delete(id: Uuid): Boolean = users.removeIf { it.id == id }
    }
}
