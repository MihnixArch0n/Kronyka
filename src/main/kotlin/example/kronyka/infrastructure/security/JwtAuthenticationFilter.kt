
package example.kronyka.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authorizationHeader = request.getHeader("Authorization")

        if (
            authorizationHeader != null &&
            authorizationHeader.startsWith("Bearer ", ignoreCase = true)
        ) {
            val token = authorizationHeader.substring(7).trim()

            if (
                jwtTokenProvider.validateToken(token) &&
                SecurityContextHolder.getContext().authentication == null
            ) {
                val userId = jwtTokenProvider.getUserIdFromToken(token)

                if (userId != null) {
                    val authentication = UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        emptyList()
                    )

                    authentication.details =
                        WebAuthenticationDetailsSource()
                            .buildDetails(request)

                    SecurityContextHolder.getContext().authentication =
                        authentication
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
