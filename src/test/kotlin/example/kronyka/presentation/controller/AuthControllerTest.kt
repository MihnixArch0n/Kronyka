package example.kronyka.presentation.controller

import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.containsString
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
                "username": "alice_api_test",
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
            .andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.message").value("User registered successfully"))
            .andExpect(jsonPath("$.result.username").value("alice_api_test"))
            .andExpect(jsonPath("$.result.full_name").value("Alice Wonderland"))
            .andExpect(jsonPath("$.result.user_id").isNotEmpty)

        val loginJson = """
            {
                "username": "alice_api_test",
                "password": "SecurePassword123@"
            }
        """.trimIndent()

        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.result.access_token").isNotEmpty)
            .andExpect(jsonPath("$.result.token_type").value("Bearer"))
            .andExpect(jsonPath("$.result.expires_in").value(86400))
            .andExpect(jsonPath("$.result.user.username").value("alice_api_test"))
            .andReturn()

        val token = JsonPath.read<String>(loginResult.response.contentAsString, "$.result.access_token")

        mockMvc.perform(
            get("/api/auth/me")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.result.username").value("alice_api_test"))
            .andExpect(jsonPath("$.result.full_name").value("Alice Wonderland"))
    }

    @Test
    fun `register succeeds when fullName is sent in camelCase via alias`() {
        val registerJson = """
            {
                "username": "camel_case_api_user",
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
            .andExpect(jsonPath("$.code").value(1000))
            .andExpect(jsonPath("$.result.username").value("camel_case_api_user"))
            .andExpect(jsonPath("$.result.full_name").value("Camel Case User"))
    }

    @Test
    fun `register fails with 400 Bad Request when full_name is missing and omits null result`() {
        val registerJson = """
            {
                "username": "missing_fn_api_user",
                "password": "SecurePassword123@"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(1005))
            .andExpect(jsonPath("$.message").value(containsString("Full name is required")))
            .andExpect(jsonPath("$.result").doesNotExist())
    }

    @Test
    fun `register fails with 409 Conflict when username already exists`() {
        val registerJson = """
            {
                "username": "duplicate_api_user",
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
            .andExpect(jsonPath("$.code").value(1001))
            .andExpect(jsonPath("$.result").doesNotExist())
    }

    @Test
    fun `login fails with 401 when password is incorrect`() {
        val registerJson = """
            {
                "username": "bad_pass_api_user",
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
                "username": "bad_pass_api_user",
                "password": "WrongPassword999@"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongLoginJson)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value(1002))
            .andExpect(jsonPath("$.result").doesNotExist())
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
            .andExpect(jsonPath("$.code").value(1005))
            .andExpect(jsonPath("$.result").doesNotExist())
    }
}
