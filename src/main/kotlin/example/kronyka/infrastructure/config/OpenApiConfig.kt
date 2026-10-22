package example.kronyka.infrastructure.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
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
        return OpenAPI()
            .info(
                Info()
                    .title("Kronyka Auction API")
                    .description("High-performance sealed-bid Vickrey & fast-paced Dutch auction platform")
                    .version("0.0.1")
                    .contact(Contact().name("Kronyka Engineering"))
            )
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName))
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
