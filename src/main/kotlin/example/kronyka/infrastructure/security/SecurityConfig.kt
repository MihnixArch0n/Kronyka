
package example.kronyka.infrastructure.security

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun jwtFilterRegistration(): FilterRegistrationBean<JwtAuthenticationFilter> {
        return FilterRegistrationBean(jwtAuthenticationFilter).apply {
            isEnabled = false
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                // Authentication endpoints
                it.requestMatchers("/api/auth/**").permitAll()

                // API documentation
                it.requestMatchers(
                    "/scalar",
                    "/scalar/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml"
                ).permitAll()

                // Dutch Auction: require JWT
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/auctions/dutch"
                ).authenticated()

                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/auctions/*/buy"
                ).authenticated()

                // Other endpoints retain existing access rules
                it.anyRequest().permitAll()
            }
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}
