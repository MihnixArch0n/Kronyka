package example.kronyka.infrastructure.security

import example.kronyka.domain.model.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.time.Clock
import java.util.Base64
import kotlin.uuid.Uuid
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class JwtTokenProvider(
    private val objectMapper: ObjectMapper,
    @Value("\${jwt.secret:kronyka-default-super-secret-hmac-sha256-key-32-chars-minimum!}")
    private val secret: String,
    @Value("\${jwt.expiration:86400}")
    val validityInSeconds: Long = 86400
) {
    private val base64UrlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val base64UrlDecoder = Base64.getUrlDecoder()

    fun generateToken(user: User): String {
        val now = Clock.System.now().epochSeconds
        val exp = now + validityInSeconds

        val headerMap = mapOf("alg" to "HS256", "typ" to "JWT")
        val payloadMap = mapOf(
            "sub" to user.id.toString(),
            "username" to user.username,
            "iat" to now,
            "exp" to exp
        )

        val headerEncoded = base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(headerMap))
        val payloadEncoded = base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(payloadMap))

        val content = "$headerEncoded.$payloadEncoded"
        val signature = sign(content)

        return "$content.$signature"
    }

    fun validateToken(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return false

            val header = objectMapper.readTree(base64UrlDecoder.decode(parts[0]))
            if (header.get("alg")?.asString() != "HS256") return false

            val content = "${parts[0]}.${parts[1]}"
            val expectedSignature = sign(content)

            if (!MessageDigest.isEqual(
                    expectedSignature.toByteArray(StandardCharsets.UTF_8),
                    parts[2].toByteArray(StandardCharsets.UTF_8)
                )
            ) {
                return false
            }

            val payloadJson = String(base64UrlDecoder.decode(parts[1]), StandardCharsets.UTF_8)
            val payload = objectMapper.readTree(payloadJson)

            val exp = payload.get("exp")?.asLong() ?: return false
            Clock.System.now().epochSeconds < exp && getUserIdFromToken(token) != null
        } catch (_: Exception) {
            false
        }
    }

    fun getUserIdFromToken(token: String): Uuid? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payloadJson = String(base64UrlDecoder.decode(parts[1]), StandardCharsets.UTF_8)
            val payload = objectMapper.readTree(payloadJson)
            val sub = payload.get("sub")?.asString() ?: return null
            Uuid.parse(sub)
        } catch (_: Exception) {
            null
        }
    }

    fun getUsernameFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            val payloadJson = String(base64UrlDecoder.decode(parts[1]), StandardCharsets.UTF_8)
            val payload = objectMapper.readTree(payloadJson)
            payload.get("username")?.asString()
        } catch (_: Exception) {
            null
        }
    }

    private fun sign(content: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val rawHmac = mac.doFinal(content.toByteArray(StandardCharsets.UTF_8))
        return base64UrlEncoder.encodeToString(rawHmac)
    }
}
