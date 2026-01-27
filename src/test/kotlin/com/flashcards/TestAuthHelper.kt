package com.flashcards

import com.flashcards.auth.AuthService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.http.MediaType
import java.util.UUID

/**
 * Extension function to add test user authentication header to MockMvc requests.
 * Uses the sentinel user ID for backwards compatibility with existing tests.
 */
fun MockHttpServletRequestBuilder.withTestUser(): MockHttpServletRequestBuilder {
    return this.header("X-Test-User-ID", "sentinel")
}

/**
 * Extension function to add test user authentication header with a specific user ID.
 */
fun MockHttpServletRequestBuilder.withTestUser(userId: UUID): MockHttpServletRequestBuilder {
    return this.header("X-Test-User-ID", userId.toString())
}

/**
 * Helper class for authentication in tests.
 * Provides methods to create test users and get authorization headers.
 */
@Component
class TestAuthHelper(
    @Autowired val authService: AuthService
) {
    /**
     * Register a test user and return the access token.
     */
    fun registerAndGetToken(
        email: String = "testuser-${UUID.randomUUID()}@example.com",
        password: String = "testpassword123",
        displayName: String = "Test User"
    ): String {
        val result = authService.register(
            email = email,
            password = password,
            displayName = displayName
        )
        return result.tokens.accessToken
    }

    /**
     * Get Authorization header with Bearer token.
     */
    fun authHeader(accessToken: String): HttpHeaders {
        return HttpHeaders().apply {
            setBearerAuth(accessToken)
        }
    }

    /**
     * Get user ID from a token by registering and getting the ID.
     */
    fun getUserIdFromToken(accessToken: String): UUID {
        // The token subject contains the user ID
        val parts = accessToken.split(".")
        if (parts.size != 3) throw IllegalArgumentException("Invalid token")

        val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        val subMatch = """"sub":"([^"]+)"""".toRegex().find(payload)
        return UUID.fromString(subMatch?.groupValues?.get(1) ?: throw IllegalArgumentException("No sub claim"))
    }

    companion object {
        // Default test user token for sentinel user ID (for backwards compatibility)
        private var cachedDefaultToken: String? = null

        /**
         * Get or create a default test token.
         */
        fun getDefaultTestToken(authHelper: TestAuthHelper): String {
            if (cachedDefaultToken == null) {
                cachedDefaultToken = authHelper.registerAndGetToken(
                    email = "default-test@example.com",
                    displayName = "Default Test User"
                )
            }
            return cachedDefaultToken!!
        }

        /**
         * Clear the cached default token (call in @BeforeEach if needed).
         */
        fun clearCache() {
            cachedDefaultToken = null
        }
    }
}
