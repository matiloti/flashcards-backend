package com.flashcards.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Filter that extracts JWT from Authorization header and sets SecurityContext.
 */
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)

            try {
                val claims = jwtService.validateToken(token)
                val userId = UUID.fromString(claims.subject)
                val email = claims["email"] as String

                val authentication = JwtAuthentication(userId, email)
                SecurityContextHolder.getContext().authentication = authentication
            } catch (e: JwtException) {
                // Invalid token - continue without authentication
                // The endpoint security will reject if auth required
            } catch (e: IllegalArgumentException) {
                // Invalid UUID in subject
            }
        }

        chain.doFilter(request, response)
    }
}
