package example.kronyka.infrastructure.config

import example.kronyka.domain.model.AuctionStatus
import example.kronyka.domain.model.AuctionType
import example.kronyka.presentation.dto.AuctionStatusSerializer
import example.kronyka.presentation.dto.AuctionTypeSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(String::class.java, Uuid::class.java) { Uuid.parse(it) }
    }

    @Bean
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        namingStrategy = JsonNamingStrategy.SnakeCase
        serializersModule = SerializersModule {
            contextual(AuctionStatus::class, AuctionStatusSerializer)
            contextual(AuctionType::class, AuctionTypeSerializer)
        }
    }

    @Bean
    fun kotlinCustomTypesModule(): JacksonModule =
        SimpleModule("KotlinCustomTypesModule").apply {
            addDeserializer(Instant::class.java, object : ValueDeserializer<Instant>() {
                override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant =
                    Instant.parse(p.string)
            })
            addSerializer(Instant::class.java, object : ValueSerializer<Instant>() {
                override fun serialize(value: Instant, gen: JsonGenerator, ctxt: SerializationContext) {
                    gen.writeString(value.toString())
                }
            })
            addDeserializer(Uuid::class.java, object : ValueDeserializer<Uuid>() {
                override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Uuid =
                    Uuid.parse(p.string)
            })
            addSerializer(Uuid::class.java, object : ValueSerializer<Uuid>() {
                override fun serialize(value: Uuid, gen: JsonGenerator, ctxt: SerializationContext) {
                    gen.writeString(value.toString())
                }
            })
        }
}
