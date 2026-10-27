package example.kronyka.presentation.dto

import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AuthSerializationTest {
    @Test
    fun `profile uses native UUID and ISO instant strings with snake case fields`() {
        val profile = UserProfileResponse(
            userId = Uuid.parse("ec4602e7-d309-44c1-b59d-eaa065f9d026"),
            username = "seller",
            fullName = "Test Seller",
            createdAt = Instant.parse("2026-10-27T10:00:00Z")
        )

        val encoded = Json.encodeToString(profile)
        val fields = Json.parseToJsonElement(encoded).jsonObject
        fields.getValue("user_id").jsonPrimitive.content shouldBe profile.userId.toString()
        fields.getValue("created_at").jsonPrimitive.content shouldBe "2026-10-27T10:00:00Z"
        fields.getValue("full_name").jsonPrimitive.content shouldBe "Test Seller"
        Json.decodeFromString<UserProfileResponse>(encoded) shouldBe profile
    }
}