package com.flashcards.user

import com.flashcards.auth.InvalidDisplayNameException
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
class UserServiceTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var userService: UserService

    @Autowired
    lateinit var userRepository: UserRepository

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setup() {
        // Clean up
        jdbcTemplate.execute("DELETE FROM refresh_tokens WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM deck_tags")
        jdbcTemplate.execute("DELETE FROM tags WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks WHERE user_id != '00000000-0000-0000-0000-000000000001'")
        jdbcTemplate.execute("DELETE FROM users WHERE id != '00000000-0000-0000-0000-000000000001'")

        // Create test user
        val user = userRepository.create(
            email = "userservice-${UUID.randomUUID()}@example.com",
            passwordHash = "\$2a\$12\$hash",
            displayName = "Test User"
        )
        testUserId = user.id
    }

    // =========================================================================
    // getProfile() tests
    // =========================================================================

    @Test
    fun `getProfile returns user profile with stats`() {
        val profile = userService.getProfile(testUserId)

        assertThat(profile).isNotNull
        assertThat(profile!!.user.id).isEqualTo(testUserId)
        assertThat(profile.stats.deckCount).isEqualTo(0)
        assertThat(profile.stats.cardCount).isEqualTo(0)
    }

    @Test
    fun `getProfile returns correct deck and card counts`() {
        // Create decks and cards for the user
        val deckId1 = UUID.randomUUID()
        val deckId2 = UUID.randomUUID()

        jdbcTemplate.update(
            """
            INSERT INTO decks (id, name, deck_type, user_id, created_at, updated_at)
            VALUES (?, 'Deck 1', 'STUDY', ?, NOW(), NOW())
            """.trimIndent(),
            deckId1, testUserId
        )
        jdbcTemplate.update(
            """
            INSERT INTO decks (id, name, deck_type, user_id, created_at, updated_at)
            VALUES (?, 'Deck 2', 'STUDY', ?, NOW(), NOW())
            """.trimIndent(),
            deckId2, testUserId
        )

        // Add 3 cards to deck 1, 2 cards to deck 2
        repeat(3) {
            jdbcTemplate.update(
                "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (?, ?, 'Q', 'A')",
                UUID.randomUUID(), deckId1
            )
        }
        repeat(2) {
            jdbcTemplate.update(
                "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (?, ?, 'Q', 'A')",
                UUID.randomUUID(), deckId2
            )
        }

        val profile = userService.getProfile(testUserId)

        assertThat(profile).isNotNull
        assertThat(profile!!.stats.deckCount).isEqualTo(2)
        assertThat(profile.stats.cardCount).isEqualTo(5)
    }

    @Test
    fun `getProfile returns null for non-existent user`() {
        val nonExistentId = UUID.randomUUID()

        val profile = userService.getProfile(nonExistentId)

        assertThat(profile).isNull()
    }

    // =========================================================================
    // updateDisplayName() tests
    // =========================================================================

    @Test
    fun `updateDisplayName updates and returns user`() {
        val updated = userService.updateDisplayName(testUserId, "New Display Name")

        assertThat(updated).isNotNull
        assertThat(updated!!.displayName).isEqualTo("New Display Name")
    }

    @Test
    fun `updateDisplayName trims whitespace`() {
        val updated = userService.updateDisplayName(testUserId, "  Trimmed Name  ")

        assertThat(updated).isNotNull
        assertThat(updated!!.displayName).isEqualTo("Trimmed Name")
    }

    @Test
    fun `updateDisplayName throws exception for name less than 2 chars`() {
        assertThatThrownBy {
            userService.updateDisplayName(testUserId, "X")
        }.isInstanceOf(InvalidDisplayNameException::class.java)
            .hasMessageContaining("2")
    }

    @Test
    fun `updateDisplayName throws exception for name more than 50 chars`() {
        val longName = "a".repeat(51)

        assertThatThrownBy {
            userService.updateDisplayName(testUserId, longName)
        }.isInstanceOf(InvalidDisplayNameException::class.java)
            .hasMessageContaining("50")
    }

    @Test
    fun `updateDisplayName returns null for non-existent user`() {
        val nonExistentId = UUID.randomUUID()

        val updated = userService.updateDisplayName(nonExistentId, "New Name")

        assertThat(updated).isNull()
    }
}
