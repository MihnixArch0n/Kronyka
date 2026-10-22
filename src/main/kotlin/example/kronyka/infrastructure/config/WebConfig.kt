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
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
@Configuration
class WebConfig {

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
}
