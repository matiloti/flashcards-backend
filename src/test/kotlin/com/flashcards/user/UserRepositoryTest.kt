package com.flashcards.user

import org.assertj.core.api.Assertions.assertThat
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
class UserRepositoryTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var userRepository: UserRepository

    @BeforeEach
    fun setup() {
        // Clean up in correct order (respect foreign keys)
        jdbcTemplate.execute("DELETE FROM refresh_tokens WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM users WHERE id != '00000000-0000-0000-0000-000000000001'")
    }

    // =========================================================================
    // create() tests
    // =========================================================================

    @Test
    fun `create saves user and returns it`() {
        val email = "test@example.com"
        val passwordHash = "\$2a\$12\$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8"
        val displayName = "Test User"

        val user = userRepository.create(email, passwordHash, displayName)

        assertThat(user.id).isNotNull()
        assertThat(user.email).isEqualTo(email.lowercase())
        assertThat(user.displayName).isEqualTo(displayName)
        assertThat(user.emailVerified).isFalse()
        assertThat(user.createdAt).isNotNull()
        assertThat(user.updatedAt).isNotNull()
    }

    @Test
    fun `create trims and lowercases email`() {
        val email = "  TEST@Example.COM  "
        val passwordHash = "\$2a\$12\$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8"
        val displayName = "Test User"

        val user = userRepository.create(email, passwordHash, displayName)

        assertThat(user.email).isEqualTo("test@example.com")
    }

    @Test
    fun `create trims display name`() {
        val email = "test@example.com"
        val passwordHash = "\$2a\$12\$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8"
        val displayName = "  Test User  "

        val user = userRepository.create(email, passwordHash, displayName)

        assertThat(user.displayName).isEqualTo("Test User")
    }

    // =========================================================================
    // findById() tests
    // =========================================================================

    @Test
    fun `findById returns user when found`() {
        val created = userRepository.create(
            email = "findbyid@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Find By Id User"
        )

        val found = userRepository.findById(created.id)

        assertThat(found).isNotNull
        assertThat(found!!.id).isEqualTo(created.id)
        assertThat(found.email).isEqualTo("findbyid@example.com")
    }

    @Test
    fun `findById returns null when not found`() {
        val nonExistentId = UUID.randomUUID()

        val found = userRepository.findById(nonExistentId)

        assertThat(found).isNull()
    }

    // =========================================================================
    // findByEmail() tests
    // =========================================================================

    @Test
    fun `findByEmail returns user when found`() {
        userRepository.create(
            email = "findbyemail@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Find By Email User"
        )

        val found = userRepository.findByEmail("findbyemail@example.com")

        assertThat(found).isNotNull
        assertThat(found!!.email).isEqualTo("findbyemail@example.com")
    }

    @Test
    fun `findByEmail is case-insensitive`() {
        userRepository.create(
            email = "casetest@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Case Test User"
        )

        val found = userRepository.findByEmail("CaseTest@Example.COM")

        assertThat(found).isNotNull
        assertThat(found!!.email).isEqualTo("casetest@example.com")
    }

    @Test
    fun `findByEmail returns null when not found`() {
        val found = userRepository.findByEmail("nonexistent@example.com")

        assertThat(found).isNull()
    }

    // =========================================================================
    // getPasswordHash() tests
    // =========================================================================

    @Test
    fun `getPasswordHash returns hash when user exists`() {
        val passwordHash = "\$2a\$12\$uniqueHashForTest123456789012"
        userRepository.create(
            email = "hashtest@example.com",
            passwordHash = passwordHash,
            displayName = "Hash Test User"
        )

        val hash = userRepository.getPasswordHash("hashtest@example.com")

        assertThat(hash).isEqualTo(passwordHash)
    }

    @Test
    fun `getPasswordHash is case-insensitive`() {
        val passwordHash = "\$2a\$12\$caseHashTest12345678901234"
        userRepository.create(
            email = "hashcase@example.com",
            passwordHash = passwordHash,
            displayName = "Hash Case User"
        )

        val hash = userRepository.getPasswordHash("HASHCASE@Example.COM")

        assertThat(hash).isEqualTo(passwordHash)
    }

    @Test
    fun `getPasswordHash returns null when user not found`() {
        val hash = userRepository.getPasswordHash("nonexistent@example.com")

        assertThat(hash).isNull()
    }

    // =========================================================================
    // existsByEmail() tests
    // =========================================================================

    @Test
    fun `existsByEmail returns true when email exists`() {
        userRepository.create(
            email = "exists@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Exists User"
        )

        val exists = userRepository.existsByEmail("exists@example.com")

        assertThat(exists).isTrue()
    }

    @Test
    fun `existsByEmail is case-insensitive`() {
        userRepository.create(
            email = "existscase@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Exists Case User"
        )

        val exists = userRepository.existsByEmail("ExistsCase@Example.COM")

        assertThat(exists).isTrue()
    }

    @Test
    fun `existsByEmail returns false when email does not exist`() {
        val exists = userRepository.existsByEmail("doesnotexist@example.com")

        assertThat(exists).isFalse()
    }

    // =========================================================================
    // updateDisplayName() tests
    // =========================================================================

    @Test
    fun `updateDisplayName updates and returns true`() {
        val user = userRepository.create(
            email = "updatename@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Old Name"
        )

        val updated = userRepository.updateDisplayName(user.id, "New Name")

        assertThat(updated).isTrue()

        val found = userRepository.findById(user.id)
        assertThat(found!!.displayName).isEqualTo("New Name")
    }

    @Test
    fun `updateDisplayName trims display name`() {
        val user = userRepository.create(
            email = "updatetrim@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Old Name"
        )

        userRepository.updateDisplayName(user.id, "  New Trimmed Name  ")

        val found = userRepository.findById(user.id)
        assertThat(found!!.displayName).isEqualTo("New Trimmed Name")
    }

    @Test
    fun `updateDisplayName returns false when user not found`() {
        val nonExistentId = UUID.randomUUID()

        val updated = userRepository.updateDisplayName(nonExistentId, "New Name")

        assertThat(updated).isFalse()
    }

    // =========================================================================
    // updatePasswordHash() tests
    // =========================================================================

    @Test
    fun `updatePasswordHash updates and returns true`() {
        val user = userRepository.create(
            email = "updatepass@example.com",
            passwordHash = "\$2a\$12\$oldHash",
            displayName = "Update Pass User"
        )

        val updated = userRepository.updatePasswordHash(user.id, "\$2a\$12\$newHash")

        assertThat(updated).isTrue()

        val hash = userRepository.getPasswordHash("updatepass@example.com")
        assertThat(hash).isEqualTo("\$2a\$12\$newHash")
    }

    @Test
    fun `updatePasswordHash returns false when user not found`() {
        val nonExistentId = UUID.randomUUID()

        val updated = userRepository.updatePasswordHash(nonExistentId, "\$2a\$12\$newHash")

        assertThat(updated).isFalse()
    }
}
