package example.kronyka.infrastructure.config

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.UUIDSchema
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.lang.reflect.Type
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Global OpenAPI ModelConverter for SpringDoc and Scalar.
 *
 * Intercepts Kotlin's standard library types (kotlin.uuid.Uuid and kotlin.time.Instant)
 * and resolves them as OpenAPI scalar schemas (format: "uuid" and format: "date-time")
 * so that Scalar UI correctly displays string input fields instead of introspecting
 * their internal fields as complex objects.
 */
@Component
class KotlinTypesOpenApiModelConverter : ModelConverter {

    @PostConstruct
    fun register() {
        ModelConverters.getInstance().addConverter(this)
    }

    override fun resolve(
        annotatedType: AnnotatedType,
        context: ModelConverterContext,
        chain: MutableIterator<ModelConverter>
    ): Schema<*>? {
        val type = annotatedType.type

        if (isTargetType(type, Uuid::class.java)) {
            return UUIDSchema().apply {
                example = "0192e2b3-7a20-72c0-8a43-02f5e3ef5499"
                description = "Kotlin standard UUID (v4/v7)"
            }
        }

        if (isTargetType(type, Instant::class.java)) {
            return DateTimeSchema().apply {
                example = "2026-10-22T06:00:00Z"
                description = "ISO-8601 Instant timestamp"
            }
        }

        return if (chain.hasNext()) chain.next().resolve(annotatedType, context, chain) else null
    }

    private fun isTargetType(type: Type?, targetClass: Class<*>): Boolean {
        if (type == null) return false
        if (type == targetClass) return true
        if (type is Class<*> && targetClass.isAssignableFrom(type)) return true
        val typeName = type.typeName
        return typeName == targetClass.name || typeName.startsWith("${targetClass.name}<")
    }
}
