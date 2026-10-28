package example.kronyka.infrastructure.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import com.scalar.maven.webmvc.ScalarWebMvcAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(ScalarWebMvcAutoConfiguration::class)
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "BearerAuth"
        val descriptionMarkdown = """
            # Kronyka High-Performance Auction Platform

            Kronyka is a high-performance auction engine implementing dual auction paradigms:

            ### 1. Sealed-Bid Vickrey (Second-Price) Engine
            A 3-phase cryptographic auction protecting bidder valuations:
            - **Phase 1 (Commit)**: Bidders submit SHA-256 blind hash commitments (`SHA-256(actual_bid + "_" + secret_salt)`) and escrow deposits (`lockup_deposit >= reserve_price`).
            - **Phase 2 (Reveal)**: Bidders reveal `actual_bid` and `secret_salt`. The engine verifies commitment validity. Mismatched commitments may be retried until the reveal window closes.
            - **Phase 3 (Settlement)**: Automated background scheduler (`VickreySettlementScheduler`) triggers after `reveal_end_time`. The highest bidder wins at the 2nd highest bid price (or reserve price if sole bidder); losing deposits are fully refunded.

            ### 2. Descending-Price Dutch Engine
            A real-time descending-price auction:
            - Effective price decreases continuously: `current_price = max(floor_price, start_price - floor((now - start_time) / step_interval) * step_decrement)`.
            - Instant "Buy Now" is protected by optimistic concurrency (`version` check). The first concurrent submission acquires the item; conflicting bids receive `409 Conflict`.

            ### Standard Response Envelope
            All endpoints return Kronyka's standard envelope:
            - **Success**: `{ "code": 1000, "message": null, "result": { ... } }`
            - **Error**: `{ "code": <ErrorCode>, "message": "<Error details>", "result": null }`

            ### Authentication Policy
            - **Public Endpoints**: `/api/auth/register`, `/api/auth/login`, `GET /api/auctions`, `GET /api/auctions/{id}`, `GET /api/auctions/{id}/price`.
            - **Protected Endpoints**: Require HTTP header `Authorization: Bearer <jwt-token>`.
        """.trimIndent()

        return OpenAPI()
            .info(
                Info()
                    .title("Kronyka Auction API")
                    .description(descriptionMarkdown)
                    .version("0.0.1")
                    .contact(Contact().name("Kronyka Engineering"))
            )
            .components(
                Components().addSecuritySchemes(
                    securitySchemeName,
                    SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Provide JWT token in format: Bearer <token>")
                )
            )
    }
}
