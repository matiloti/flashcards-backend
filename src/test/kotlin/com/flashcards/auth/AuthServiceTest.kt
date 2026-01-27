package com.flashcards.auth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var authService: AuthService

    @BeforeEach
    fun setup() {
        // Clean up in correct order (respect foreign keys)
        jdbcTemplate.execute("DELETE FROM refresh_tokens WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM users WHERE id != '00000000-0000-0000-0000-000000000001'")
    }

    // =========================================================================
    // register() tests
    // =========================================================================

    @Test
    fun `register creates user and returns tokens`() {
        val result = authService.register(
            email = "register@example.com",
            password = "password123",
            displayName = "Register Test"
        )

        assertThat(result.user.email).isEqualTo("register@example.com")
        assertThat(result.user.displayName).isEqualTo("Register Test")
        assertThat(result.user.emailVerified).isFalse()
        assertThat(result.tokens.accessToken).isNotBlank()
        assertThat(result.tokens.refreshToken).hasSize(64)
        assertThat(result.tokens.accessTokenExpiresIn).isEqualTo(900)
        assertThat(result.tokens.refreshTokenExpiresIn).isEqualTo(2592000)
    }

    @Test
    fun `register normalizes email to lowercase`() {
        val result = authService.register(
            email = "UPPERCASE@EXAMPLE.COM",
            password = "password123",
            displayName = "Case Test"
        )

        assertThat(result.user.email).isEqualTo("uppercase@example.com")
    }

    @Test
    fun `register trims display name`() {
        val result = authService.register(
            email = "trim@example.com",
            password = "password123",
            displayName = "  Trimmed Name  "
        )

        assertThat(result.user.displayName).isEqualTo("Trimmed Name")
    }

    @Test
    fun `register throws EmailAlreadyExistsException for duplicate email`() {
        authService.register(
            email = "duplicate@example.com",
            password = "password123",
            displayName = "First User"
        )

        assertThatThrownBy {
            authService.register(
                email = "duplicate@example.com",
                password = "different123",
                displayName = "Second User"
            )
        }.isInstanceOf(EmailAlreadyExistsException::class.java)
    }

    @Test
    fun `register throws EmailAlreadyExistsException for case-insensitive duplicate`() {
        authService.register(
            email = "casedupe@example.com",
            password = "password123",
            displayName = "First User"
        )

        assertThatThrownBy {
            authService.register(
                email = "CASEDUPE@Example.COM",
                password = "different123",
                displayName = "Second User"
            )
        }.isInstanceOf(EmailAlreadyExistsException::class.java)
    }

    @Test
    fun `register throws InvalidPasswordException for password less than 8 chars`() {
        assertThatThrownBy {
            authService.register(
                email = "shortpass@example.com",
                password = "short",
                displayName = "Short Pass"
            )
        }.isInstanceOf(InvalidPasswordException::class.java)
            .hasMessageContaining("8")
    }

    @Test
    fun `register throws InvalidPasswordException for password more than 72 chars`() {
        val longPassword = "a".repeat(73)

        assertThatThrownBy {
            authService.register(
                email = "longpass@example.com",
                password = longPassword,
                displayName = "Long Pass"
            )
        }.isInstanceOf(InvalidPasswordException::class.java)
            .hasMessageContaining("72")
    }

    @Test
    fun `register throws InvalidDisplayNameException for display name less than 2 chars`() {
        assertThatThrownBy {
            authService.register(
                email = "shortname@example.com",
                password = "password123",
                displayName = "X"
            )
        }.isInstanceOf(InvalidDisplayNameException::class.java)
            .hasMessageContaining("2")
    }

    @Test
    fun `register throws InvalidDisplayNameException for display name more than 50 chars`() {
        val longName = "a".repeat(51)

        assertThatThrownBy {
            authService.register(
                email = "longname@example.com",
                password = "password123",
                displayName = longName
            )
        }.isInstanceOf(InvalidDisplayNameException::class.java)
            .hasMessageContaining("50")
    }

    // =========================================================================
    // login() tests
    // =========================================================================

    @Test
    fun `login returns tokens for valid credentials`() {
        authService.register(
            email = "login@example.com",
            password = "correctpassword",
            displayName = "Login Test"
        )

        val result = authService.login(
            email = "login@example.com",
            password = "correctpassword"
        )

        assertThat(result.user.email).isEqualTo("login@example.com")
        assertThat(result.tokens.accessToken).isNotBlank()
        assertThat(result.tokens.refreshToken).hasSize(64)
    }

    @Test
    fun `login is case-insensitive for email`() {
        authService.register(
            email = "logincase@example.com",
            password = "password123",
            displayName = "Login Case"
        )

        val result = authService.login(
            email = "LOGINCASE@Example.COM",
            password = "password123"
        )

        assertThat(result.user.email).isEqualTo("logincase@example.com")
    }

    @Test
    fun `login throws InvalidCredentialsException for wrong password`() {
        authService.register(
            email = "wrongpass@example.com",
            password = "correctpassword",
            displayName = "Wrong Pass"
        )

        assertThatThrownBy {
            authService.login(
                email = "wrongpass@example.com",
                password = "wrongpassword"
            )
        }.isInstanceOf(InvalidCredentialsException::class.java)
    }

    @Test
    fun `login throws InvalidCredentialsException for non-existent user`() {
        assertThatThrownBy {
            authService.login(
                email = "nonexistent@example.com",
                password = "password123"
            )
        }.isInstanceOf(InvalidCredentialsException::class.java)
    }

    // =========================================================================
    // refresh() tests
    // =========================================================================

    @Test
    fun `refresh returns new tokens for valid refresh token`() {
        val registerResult = authService.register(
            email = "refresh@example.com",
            password = "password123",
            displayName = "Refresh Test"
        )

        val refreshResult = authService.refresh(registerResult.tokens.refreshToken)

        assertThat(refreshResult.accessToken).isNotBlank()
        assertThat(refreshResult.refreshToken).hasSize(64)
        // Refresh token should be different (always new random token)
        assertThat(refreshResult.refreshToken).isNotEqualTo(registerResult.tokens.refreshToken)
        // Note: Access tokens may be same if generated within same second (same iat/exp)
        // The important thing is that they're valid tokens
    }

    @Test
    fun `refresh revokes old refresh token`() {
        val registerResult = authService.register(
            email = "refreshrevoke@example.com",
            password = "password123",
            displayName = "Refresh Revoke"
        )
        val oldRefreshToken = registerResult.tokens.refreshToken

        authService.refresh(oldRefreshToken)

        // Old token should no longer work
        assertThatThrownBy {
            authService.refresh(oldRefreshToken)
        }.isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun `refresh throws InvalidTokenException for invalid token`() {
        assertThatThrownBy {
            authService.refresh("invalidtoken" + "x".repeat(52))
        }.isInstanceOf(InvalidTokenException::class.java)
    }

    // =========================================================================
    // logout() tests
    // =========================================================================

    @Test
    fun `logout revokes refresh token`() {
        val registerResult = authService.register(
            email = "logout@example.com",
            password = "password123",
            displayName = "Logout Test"
        )

        authService.logout(registerResult.tokens.refreshToken)

        // Token should no longer work
        assertThatThrownBy {
            authService.refresh(registerResult.tokens.refreshToken)
        }.isInstanceOf(InvalidTokenException::class.java)
    }

    @Test
    fun `logout is idempotent`() {
        val registerResult = authService.register(
            email = "logoutidempotent@example.com",
            password = "password123",
            displayName = "Logout Idempotent"
        )

        // Multiple logouts should not throw
        authService.logout(registerResult.tokens.refreshToken)
        authService.logout(registerResult.tokens.refreshToken)
        authService.logout(registerResult.tokens.refreshToken)
    }

    // =========================================================================
    // logoutAll() tests
    // =========================================================================

    @Test
    fun `logoutAll revokes all refresh tokens for user`() {
        val registerResult = authService.register(
            email = "logoutall@example.com",
            password = "password123",
            displayName = "Logout All"
        )

        // Login again to create another refresh token
        val loginResult = authService.login(
            email = "logoutall@example.com",
            password = "password123"
        )

        authService.logoutAll(registerResult.user.id)

        // Both tokens should no longer work
        assertThatThrownBy {
            authService.refresh(registerResult.tokens.refreshToken)
        }.isInstanceOf(InvalidTokenException::class.java)

        assertThatThrownBy {
            authService.refresh(loginResult.tokens.refreshToken)
        }.isInstanceOf(InvalidTokenException::class.java)
    }
}
