package example.kronyka.infrastructure.security

import example.kronyka.presentation.dto.ApiResponse
import example.kronyka.presentation.dto.ErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        tokenProvider: JwtTokenProvider,
        objectMapper: ObjectMapper
    ): SecurityFilterChain {
        val entryPoint = AuthenticationEntryPoint { _, response, _ ->
            response.status = ErrorCode.UNAUTHENTICATED.httpStatus.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            response.writer.write(objectMapper.writeValueAsString(ApiResponse.error<Unit>(ErrorCode.UNAUTHENTICATED)))
        }
        http
            // Bearer tokens are sent explicitly in headers, not in browser cookies.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .addFilterBefore(JwtAuthFilter(tokenProvider, entryPoint), UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/auctions", "/api/auctions/{id}").permitAll()
                it.anyRequest().authenticated()
            }
        return http.build()
    }
}
