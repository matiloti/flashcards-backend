package com.flashcards.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PasswordServiceTest {

    private val passwordService = PasswordService()

    // =========================================================================
    // hash() tests
    // =========================================================================

    @Test
    fun `hash returns bcrypt hash starting with $2a$12$`() {
        val password = "testPassword123"

        val hash = passwordService.hash(password)

        assertThat(hash).startsWith("\$2a\$12\$")
    }

    @Test
    fun `hash returns 60-character hash`() {
        val password = "testPassword123"

        val hash = passwordService.hash(password)

        assertThat(hash).hasSize(60)
    }

    @Test
    fun `hash generates different hashes for same password`() {
        val password = "testPassword123"

        val hash1 = passwordService.hash(password)
        val hash2 = passwordService.hash(password)

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `hash handles empty password`() {
        val password = ""

        val hash = passwordService.hash(password)

        assertThat(hash).startsWith("\$2a\$12\$")
        assertThat(hash).hasSize(60)
    }

    @Test
    fun `hash handles unicode characters`() {
        val password = "pAssw0rd!@#\u4e2d\u6587"

        val hash = passwordService.hash(password)

        assertThat(hash).startsWith("\$2a\$12\$")
    }

    // =========================================================================
    // verify() tests
    // =========================================================================

    @Test
    fun `verify returns true for correct password`() {
        val password = "correctPassword123"
        val hash = passwordService.hash(password)

        val result = passwordService.verify(password, hash)

        assertThat(result).isTrue()
    }

    @Test
    fun `verify returns false for incorrect password`() {
        val password = "correctPassword123"
        val wrongPassword = "wrongPassword456"
        val hash = passwordService.hash(password)

        val result = passwordService.verify(wrongPassword, hash)

        assertThat(result).isFalse()
    }

    @Test
    fun `verify returns false for similar password`() {
        val password = "Password123"
        val similarPassword = "password123" // different case
        val hash = passwordService.hash(password)

        val result = passwordService.verify(similarPassword, hash)

        assertThat(result).isFalse()
    }

    @Test
    fun `verify works with unicode passwords`() {
        val password = "pAssw0rd!@#\u4e2d\u6587"
        val hash = passwordService.hash(password)

        val result = passwordService.verify(password, hash)

        assertThat(result).isTrue()
    }

    @Test
    fun `verify handles empty password correctly`() {
        val password = ""
        val hash = passwordService.hash(password)

        val result = passwordService.verify(password, hash)

        assertThat(result).isTrue()
    }

    @Test
    fun `verify returns false for empty password against non-empty hash`() {
        val password = "somePassword"
        val hash = passwordService.hash(password)

        val result = passwordService.verify("", hash)

        assertThat(result).isFalse()
    }
}
