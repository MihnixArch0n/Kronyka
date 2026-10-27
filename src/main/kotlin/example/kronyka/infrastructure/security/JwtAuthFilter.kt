package example.kronyka.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthFilter(
    private val tokenProvider: JwtTokenProvider,
    private val authenticationEntryPoint: AuthenticationEntryPoint
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header != null) {
            val token = header.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring(7)?.trim()
            val userId = token?.takeIf { it.isNotEmpty() && tokenProvider.validateToken(it) }
                ?.let(tokenProvider::getUserIdFromToken)

            if (userId == null) {
                SecurityContextHolder.clearContext()
                authenticationEntryPoint.commence(request, response, BadCredentialsException("Invalid JWT"))
                return
            }

            val authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
        }

        filterChain.doFilter(request, response)
    }
}