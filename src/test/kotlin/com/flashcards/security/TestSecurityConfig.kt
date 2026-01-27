package com.flashcards.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.util.UUID

/**
 * Test security configuration that allows tests to run with or without authentication.
 *
 * For tests that use the @WithMockJwtUser annotation or provide their own token,
 * authentication will be validated. For other tests, a default user is used.
 */
@Configuration
@EnableWebSecurity
@Profile("test")
class TestSecurityConfig(
    private val jwtService: JwtService
) {

    /**
     * Security filter chain for tests that automatically authenticates requests
     * without a token using the sentinel user ID.
     */
    @Bean
    @Primary
    @Order(1)
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Public auth endpoints
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/register").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                    // Health check
                    .requestMatchers("/actuator/health").permitAll()
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                TestJwtAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter::class.java
            )
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint(JwtAuthenticationEntryPoint())
            }

        return http.build()
    }
}

/**
 * Test-specific JWT filter that:
 * 1. Uses the real JWT validation if a token is provided
 * 2. Checks for special "X-Test-User-ID" header for user ID override
 * 3. Checks for special "X-Test-No-Auth" header to bypass auth (for testing 401 scenarios)
 * 4. Auto-authenticates with sentinel user for backwards compatibility with existing tests
 */
class TestJwtAuthenticationFilter(
    private val jwtService: JwtService
) : org.springframework.web.filter.OncePerRequestFilter() {

    companion object {
        // Sentinel user ID used in tests (same as the default user in migration)
        val SENTINEL_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        // Header name for test user ID override
        const val TEST_USER_HEADER = "X-Test-User-ID"
        // Header name to disable auto-auth (for testing 401 scenarios)
        const val TEST_NO_AUTH_HEADER = "X-Test-No-Auth"
    }

    override fun doFilterInternal(
        request: jakarta.servlet.http.HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
        chain: jakarta.servlet.FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        val testUserHeader = request.getHeader(TEST_USER_HEADER)
        val noAuthHeader = request.getHeader(TEST_NO_AUTH_HEADER)

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Real token provided - validate it
            val token = authHeader.substring(7)
            try {
                val claims = jwtService.validateToken(token)
                val userId = UUID.fromString(claims.subject)
                val email = claims["email"] as String
                val authentication = JwtAuthentication(userId, email)
                org.springframework.security.core.context.SecurityContextHolder.getContext().authentication = authentication
            } catch (e: Exception) {
                // Invalid token - continue without authentication
            }
        } else if (noAuthHeader != null) {
            // Explicitly no auth - don't auto-authenticate (for testing 401 responses)
        } else if (testUserHeader != null) {
            // Test user header provided - use it for user ID override
            val userId = if (testUserHeader == "sentinel") SENTINEL_USER_ID else UUID.fromString(testUserHeader)
            val authentication = JwtAuthentication(userId, "test@flashcards.local")
            org.springframework.security.core.context.SecurityContextHolder.getContext().authentication = authentication
        } else {
            // No explicit auth - auto-authenticate with sentinel user for backwards compatibility
            val authentication = JwtAuthentication(SENTINEL_USER_ID, "default@flashcards.local")
            org.springframework.security.core.context.SecurityContextHolder.getContext().authentication = authentication
        }

        chain.doFilter(request, response)
    }
}
