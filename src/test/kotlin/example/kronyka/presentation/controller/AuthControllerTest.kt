package example.kronyka.presentation.controller

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `register and login flow works successfully`() {
        val registerJson = """
            {
                "username": "alice_controller_test",
                "password": "SecurePassword123@",
                "full_name": "Alice Wonderland"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value("alice_controller_test"))
            .andExpect(jsonPath("$.full_name").value("Alice Wonderland"))
            .andExpect(jsonPath("$.user_id").isNotEmpty)

        val loginJson = """
            {
                "username": "alice_controller_test",
                "password": "SecurePassword123@"
            }
        """.trimIndent()

        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.access_token").isNotEmpty)
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(86400))
            .andExpect(jsonPath("$.user.username").value("alice_controller_test"))
            .andReturn()

        val token = JsonPath.read<String>(loginResult.response.contentAsString, "$.access_token")

        mockMvc.perform(
            get("/api/auth/me")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("alice_controller_test"))
            .andExpect(jsonPath("$.full_name").value("Alice Wonderland"))
    }

    @Test
    fun `register succeeds when fullName is sent in camelCase via alias`() {
        val registerJson = """
            {
                "username": "camel_case_user",
                "password": "SecurePassword123@",
                "fullName": "Camel Case User"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value("camel_case_user"))
            .andExpect(jsonPath("$.full_name").value("Camel Case User"))
    }

    @Test
    fun `register fails with 400 Bad Request when full_name is missing`() {
        val registerJson = """
            {
                "username": "missing_fn_user",
                "password": "SecurePassword123@"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Full name is required")))
    }

    @Test
    fun `register fails with 409 Conflict when username already exists`() {
        val registerJson = """
            {
                "username": "duplicate_user_test",
                "password": "SecurePassword123@",
                "full_name": "First User"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("USER_ALREADY_EXISTS"))
    }

    @Test
    fun `login fails with 401 when password is incorrect`() {
        val registerJson = """
            {
                "username": "bad_pass_user_test",
                "password": "CorrectPassword123@",
                "full_name": "Bad Pass User"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        ).andExpect(status().isCreated)

        val wrongLoginJson = """
            {
                "username": "bad_pass_user_test",
                "password": "WrongPassword999@"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongLoginJson)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
    }

    @Test
    fun `register fails with 400 Bad Request on validation error`() {
        val invalidJson = """
            {
                "username": "a",
                "password": "123",
                "full_name": ""
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
    }
}
