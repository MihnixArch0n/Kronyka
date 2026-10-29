package example.kronyka.infrastructure.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Json31
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
 * 1. Intercepts Kotlin's standard library types (kotlin.uuid.Uuid and kotlin.time.Instant)
 *    and resolves them as OpenAPI scalar schemas (format: "uuid" and format: "date-time")
 *    so that Scalar UI correctly displays string input fields instead of introspecting
 *    their internal fields as complex objects.
 * 2. Configures ModelResolver with SNAKE_CASE naming strategy on isolated converter mappers,
 *    ensuring all DTO schemas in Scalar / OpenAPI display fields in snake_case without
 *    breaking root OpenAPI document structure.
 */
@Component
class KotlinTypesOpenApiModelConverter : ModelConverter {

    @PostConstruct
    fun register() {
        for (isOpenApi31 in listOf(false, true)) {
            val modelConverters = ModelConverters.getInstance(isOpenApi31)
            val existing = ArrayList(modelConverters.converters)
            for (converter in existing) {
                if (converter is ModelResolver) {
                    modelConverters.removeConverter(converter)
                }
            }
            val mapper = (if (isOpenApi31) Json31.mapper() else Json.mapper()).copy().apply {
                propertyNamingStrategy = SNAKE_CASE
            }
            val modelResolver = ModelResolver(mapper).apply {
                openapi31(isOpenApi31)
            }
            modelConverters.addConverter(modelResolver)
            modelConverters.addConverter(this)
        }
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
        val raw = extractRawClass(type)
        if (raw == targetClass || (raw != null && targetClass.isAssignableFrom(raw))) return true
        val str = type.toString()
        val typeName = type.typeName
        return targetClass.name in typeName || targetClass.name in str
    }

    private fun extractRawClass(type: Type?): Class<*>? {
        if (type == null) return null
        if (type is Class<*>) return type
        try {
            val method = type.javaClass.getMethod("getRawClass")
            val raw = method.invoke(type)
            if (raw is Class<*>) return raw
        } catch (_: Throwable) {
        }
        if (type is java.lang.reflect.ParameterizedType) {
            val raw = type.rawType
            if (raw is Class<*>) return raw
        }
        return null
    }
}
