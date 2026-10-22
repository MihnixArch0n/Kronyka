package example.kronyka.infrastructure.security

import tools.jackson.databind.JsonNode
import example.kronyka.domain.model.User
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(
    controllers = [JwtSecurityTest.JwtSecurityProbe::class],
    excludeAutoConfiguration = [ExposedAutoConfiguration::class]
)
@AutoConfigureMockMvc(addFilters = true)
@Import(SecurityConfig::class, JwtTokenProvider::class, JwtSecurityTest.JwtSecurityProbe::class)
@TestPropertySource(properties = ["jwt.secret=test-jwt-secret-for-security-tests-32-chars!"])
class JwtSecurityTest {

    companion object {
        private const val TEST_SECRET = "test-jwt-secret-for-security-tests-32-chars!"
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Test
    fun `public routes do not require authentication`() {
        val publicRequests = listOf(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auctions"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auctions/${UUID.randomUUID()}")
        )

        publicRequests.forEach { request ->
            mockMvc.perform(request).andReturn().response.status shouldBe 200
        }
    }

    @Test
    fun `protected routes reject missing authentication with bearer challenge`() {
        val protectedRequests = listOf(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auctions"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auctions/${UUID.randomUUID()}/buy-dutch"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auctions/${UUID.randomUUID()}/bids"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/private")
        )

        protectedRequests.forEach { request ->
            assertUnauthenticated(mockMvc.perform(request).andReturn())
        }
    }

    @Test
    fun `method and path boundaries remain protected`() {
        val protectedRequests = listOf(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/register"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/login"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auctions/${UUID.randomUUID()}"),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auctions/${UUID.randomUUID()}/nested")
        )

        protectedRequests.forEach { request ->
            assertUnauthenticated(mockMvc.perform(request).andReturn())
        }
    }

    @Test
    fun `invalid malformed tampered and expired tokens are rejected`() {
        val user = user()
        val validToken = jwtTokenProvider.generateToken(user)
        val tamperedToken = validToken.dropLast(1) + if (validToken.last() == 'A') 'B' else 'A'
        val expiredToken = JwtTokenProvider(objectMapper, TEST_SECRET, -1).generateToken(user)

        listOf("malformed-token", tamperedToken, expiredToken).forEach { token ->
            val result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                    .header("Authorization", "Bearer $token")
            ).andReturn()

            assertUnauthenticated(result)
        }
    }

    @Test
    fun `valid token exposes its user identity and ignores spoofed header`() {
        val user = user()
        val result = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                .header("Authorization", "Bearer ${jwtTokenProvider.generateToken(user)}")
                .header("X-User-Id", UUID.randomUUID().toString())
        ).andReturn()

        result.response.status shouldBe 200
        result.response.contentAsString shouldBe user.id.toString()
    }

    @Test
    fun `authentication context does not leak into a subsequent unauthenticated request`() {
        val user = user()
        val authenticated = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
                .header("Authorization", "Bearer ${jwtTokenProvider.generateToken(user)}")
        ).andReturn()
        authenticated.response.status shouldBe 200

        val unauthenticated = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me")
        ).andReturn()

        assertUnauthenticated(unauthenticated)
    }

    private fun user(): User = User(
        id = UUID.randomUUID(),
        username = "security-test-user",
        passwordHash = "unused",
        fullName = "Security Test User",
        createdAt = LocalDateTime.now()
    )

    private fun assertUnauthenticated(result: MvcResult) {
        result.response.status shouldBe 401
        result.response.getHeader("WWW-Authenticate") shouldBe "Bearer"
        responseJson(result).get("code").asInt() shouldBe 1004
    }

    private fun responseJson(result: MvcResult): JsonNode =
        objectMapper.readTree(result.response.contentAsString)

    @RestController
    class JwtSecurityProbe {
        @GetMapping("/api/auth/me")
        fun me(@AuthenticationPrincipal userId: UUID): String = userId.toString()

        @PostMapping("/api/auth/register", "/api/auth/login")
        fun publicAuth(): String = "public"

        @GetMapping("/api/auctions", "/api/auctions/{id}")
        fun publicAuctions(): String = "public"
    }
}