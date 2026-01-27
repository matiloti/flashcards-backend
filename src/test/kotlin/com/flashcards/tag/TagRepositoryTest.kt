package com.flashcards.tag

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class TagRepositoryTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var tagRepository: TagRepository

    private val sentinelUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setup() {
        // Clean up in reverse dependency order
        jdbcTemplate.execute("DELETE FROM deck_tags")
        jdbcTemplate.execute("DELETE FROM tags")
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM card_progress")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
    }

    // =========================================================================
    // findByUserIdWithDeckCount tests
    // =========================================================================

    @Test
    fun `findByUserIdWithDeckCount returns empty list when no tags exist`() {
        val tags = tagRepository.findByUserIdWithDeckCount(sentinelUserId)
        assertThat(tags).isEmpty()
    }

    @Test
    fun `findByUserIdWithDeckCount returns tags ordered by name`() {
        // Create tags in random order
        createTag("Zebra")
        createTag("Apple")
        createTag("Mango")

        val tags = tagRepository.findByUserIdWithDeckCount(sentinelUserId)

        assertThat(tags).hasSize(3)
        assertThat(tags[0].name).isEqualTo("Apple")
        assertThat(tags[1].name).isEqualTo("Mango")
        assertThat(tags[2].name).isEqualTo("Zebra")
    }

    @Test
    fun `findByUserIdWithDeckCount returns correct deck count`() {
        val tag1 = createTag("Tag1")
        val tag2 = createTag("Tag2")
        val tag3 = createTag("Tag3")

        val deck1 = createDeck("Deck1")
        val deck2 = createDeck("Deck2")
        val deck3 = createDeck("Deck3")

        // Tag1 has 2 decks, Tag2 has 1 deck, Tag3 has 0 decks
        addTagToDeck(deck1, tag1)
        addTagToDeck(deck2, tag1)
        addTagToDeck(deck1, tag2)

        val tags = tagRepository.findByUserIdWithDeckCount(sentinelUserId)

        assertThat(tags).hasSize(3)
        val tag1Result = tags.find { it.name == "Tag1" }
        val tag2Result = tags.find { it.name == "Tag2" }
        val tag3Result = tags.find { it.name == "Tag3" }

        assertThat(tag1Result!!.deckCount).isEqualTo(2)
        assertThat(tag2Result!!.deckCount).isEqualTo(1)
        assertThat(tag3Result!!.deckCount).isEqualTo(0)
    }

    @Test
    fun `findByUserIdWithDeckCount only returns tags for specified user`() {
        val otherUserId = UUID.randomUUID()
        createTag("UserTag")
        createTagForUser("OtherUserTag", otherUserId)

        val tags = tagRepository.findByUserIdWithDeckCount(sentinelUserId)

        assertThat(tags).hasSize(1)
        assertThat(tags[0].name).isEqualTo("UserTag")
    }

    // =========================================================================
    // findByIdAndUserId tests
    // =========================================================================

    @Test
    fun `findByIdAndUserId returns tag when exists`() {
        val tagId = createTag("TestTag")

        val tag = tagRepository.findByIdAndUserId(tagId, sentinelUserId)

        assertThat(tag).isNotNull
        assertThat(tag!!.id).isEqualTo(tagId)
        assertThat(tag.name).isEqualTo("TestTag")
        assertThat(tag.userId).isEqualTo(sentinelUserId)
    }

    @Test
    fun `findByIdAndUserId returns null when tag not found`() {
        val nonExistentId = UUID.randomUUID()

        val tag = tagRepository.findByIdAndUserId(nonExistentId, sentinelUserId)

        assertThat(tag).isNull()
    }

    @Test
    fun `findByIdAndUserId returns null when tag belongs to different user`() {
        val otherUserId = UUID.randomUUID()
        val tagId = createTagForUser("OtherTag", otherUserId)

        val tag = tagRepository.findByIdAndUserId(tagId, sentinelUserId)

        assertThat(tag).isNull()
    }

    // =========================================================================
    // existsByUserIdAndNameIgnoreCase tests
    // =========================================================================

    @Test
    fun `existsByUserIdAndNameIgnoreCase returns true for exact match`() {
        createTag("Biology")

        val exists = tagRepository.existsByUserIdAndNameIgnoreCase(sentinelUserId, "Biology")

        assertThat(exists).isTrue()
    }

    @Test
    fun `existsByUserIdAndNameIgnoreCase returns true for different case`() {
        createTag("Biology")

        assertThat(tagRepository.existsByUserIdAndNameIgnoreCase(sentinelUserId, "biology")).isTrue()
        assertThat(tagRepository.existsByUserIdAndNameIgnoreCase(sentinelUserId, "BIOLOGY")).isTrue()
        assertThat(tagRepository.existsByUserIdAndNameIgnoreCase(sentinelUserId, "BiOlOgY")).isTrue()
    }

    @Test
    fun `existsByUserIdAndNameIgnoreCase returns false when not exists`() {
        createTag("Biology")

        val exists = tagRepository.existsByUserIdAndNameIgnoreCase(sentinelUserId, "Chemistry")

        assertThat(exists).isFalse()
    }

    @Test
    fun `existsByUserIdAndNameIgnoreCase returns false for other user tag`() {
        val otherUserId = UUID.randomUUID()
        createTagForUser("Biology", otherUserId)

        val exists = tagRepository.existsByUserIdAndNameIgnoreCase(sentinelUserId, "Biology")

        assertThat(exists).isFalse()
    }

    // =========================================================================
    // existsByUserIdAndNameIgnoreCaseExcludingId tests
    // =========================================================================

    @Test
    fun `existsByUserIdAndNameIgnoreCaseExcludingId returns false when only match is excluded id`() {
        val tagId = createTag("Biology")

        val exists = tagRepository.existsByUserIdAndNameIgnoreCaseExcludingId(
            sentinelUserId, "Biology", tagId
        )

        assertThat(exists).isFalse()
    }

    @Test
    fun `existsByUserIdAndNameIgnoreCaseExcludingId returns true when different tag has name`() {
        val tag1Id = createTag("Biology")
        createTag("Chemistry")

        val exists = tagRepository.existsByUserIdAndNameIgnoreCaseExcludingId(
            sentinelUserId, "Chemistry", tag1Id
        )

        assertThat(exists).isTrue()
    }

    // =========================================================================
    // save tests
    // =========================================================================

    @Test
    fun `save creates new tag with generated id`() {
        val tag = Tag(
            id = UUID.randomUUID(),
            name = "NewTag",
            userId = sentinelUserId,
            createdAt = Instant.now()
        )

        val saved = tagRepository.save(tag)

        assertThat(saved.id).isNotNull()
        assertThat(saved.name).isEqualTo("NewTag")
        assertThat(saved.userId).isEqualTo(sentinelUserId)
        assertThat(saved.createdAt).isNotNull()

        // Verify persisted
        val found = tagRepository.findByIdAndUserId(saved.id, sentinelUserId)
        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo("NewTag")
    }

    // =========================================================================
    // update tests
    // =========================================================================

    @Test
    fun `update changes tag name`() {
        val tagId = createTag("OldName")

        val tag = tagRepository.findByIdAndUserId(tagId, sentinelUserId)!!
        val updated = tagRepository.update(tag.copy(name = "NewName"))

        assertThat(updated.name).isEqualTo("NewName")

        // Verify persisted
        val found = tagRepository.findByIdAndUserId(tagId, sentinelUserId)
        assertThat(found!!.name).isEqualTo("NewName")
    }

    // =========================================================================
    // deleteById tests
    // =========================================================================

    @Test
    fun `deleteById removes tag`() {
        val tagId = createTag("ToDelete")

        tagRepository.deleteById(tagId)

        val found = tagRepository.findByIdAndUserId(tagId, sentinelUserId)
        assertThat(found).isNull()
    }

    @Test
    fun `deleteById removes associated deck_tags entries`() {
        val tagId = createTag("TagToDelete")
        val deckId = createDeck("TestDeck")
        addTagToDeck(deckId, tagId)

        tagRepository.deleteById(tagId)

        // Verify deck still exists but deck_tags entry is gone
        val deckExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decks WHERE id = ?",
            Int::class.java,
            deckId
        )
        val deckTagsCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM deck_tags WHERE tag_id = ?",
            Int::class.java,
            tagId
        )

        assertThat(deckExists).isEqualTo(1)
        assertThat(deckTagsCount).isEqualTo(0)
    }

    // =========================================================================
    // findByUserIdWithDeckCountPaginated tests
    // =========================================================================

    @Test
    fun `findByUserIdWithDeckCountPaginated returns correct page`() {
        // Create 5 tags
        createTag("Tag1")
        createTag("Tag2")
        createTag("Tag3")
        createTag("Tag4")
        createTag("Tag5")

        val page0 = tagRepository.findByUserIdWithDeckCountPaginated(sentinelUserId, 0, 2, "name", "asc")
        val page1 = tagRepository.findByUserIdWithDeckCountPaginated(sentinelUserId, 1, 2, "name", "asc")
        val page2 = tagRepository.findByUserIdWithDeckCountPaginated(sentinelUserId, 2, 2, "name", "asc")

        assertThat(page0).hasSize(2)
        assertThat(page0[0].name).isEqualTo("Tag1")
        assertThat(page0[1].name).isEqualTo("Tag2")

        assertThat(page1).hasSize(2)
        assertThat(page1[0].name).isEqualTo("Tag3")
        assertThat(page1[1].name).isEqualTo("Tag4")

        assertThat(page2).hasSize(1)
        assertThat(page2[0].name).isEqualTo("Tag5")
    }

    @Test
    fun `findByUserIdWithDeckCountPaginated sorts by name descending`() {
        createTag("Apple")
        createTag("Zebra")
        createTag("Mango")

        val tags = tagRepository.findByUserIdWithDeckCountPaginated(sentinelUserId, 0, 10, "name", "desc")

        assertThat(tags).hasSize(3)
        assertThat(tags[0].name).isEqualTo("Zebra")
        assertThat(tags[1].name).isEqualTo("Mango")
        assertThat(tags[2].name).isEqualTo("Apple")
    }

    @Test
    fun `findByUserIdWithDeckCountPaginated returns empty for out of range page`() {
        createTag("Tag1")
        createTag("Tag2")

        val tags = tagRepository.findByUserIdWithDeckCountPaginated(sentinelUserId, 10, 10, "name", "asc")

        assertThat(tags).isEmpty()
    }

    // =========================================================================
    // countByUserId tests
    // =========================================================================

    @Test
    fun `countByUserId returns zero when no tags exist`() {
        val count = tagRepository.countByUserId(sentinelUserId)
        assertThat(count).isEqualTo(0L)
    }

    @Test
    fun `countByUserId returns correct count`() {
        createTag("Tag1")
        createTag("Tag2")
        createTag("Tag3")

        val count = tagRepository.countByUserId(sentinelUserId)

        assertThat(count).isEqualTo(3L)
    }

    @Test
    fun `countByUserId only counts tags for specified user`() {
        val otherUserId = UUID.randomUUID()
        createTag("UserTag1")
        createTag("UserTag2")
        createTagForUser("OtherUserTag", otherUserId)

        val count = tagRepository.countByUserId(sentinelUserId)

        assertThat(count).isEqualTo(2L)
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private fun createTag(name: String): UUID {
        return createTagForUser(name, sentinelUserId)
    }

    private fun createTagForUser(name: String, userId: UUID): UUID {
        // Ensure the user exists first (create if not exists)
        ensureUserExists(userId)

        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO tags (id, name, user_id, created_at) VALUES (?, ?, ?, ?)",
            id, name, userId, java.sql.Timestamp.from(now)
        )
        return id
    }

    private fun ensureUserExists(userId: UUID) {
        val now = Instant.now()
        jdbcTemplate.update(
            """INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
               VALUES (?, ?, 'test-hash', 'Test User', ?, ?)
               ON CONFLICT (id) DO NOTHING""",
            userId, "test-${userId}@example.com", java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
    }

    private fun createDeck(name: String): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO decks (id, name, deck_type, user_id, created_at, updated_at) VALUES (?, ?, 'STUDY', ?, ?, ?)",
            id, name, sentinelUserId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
        return id
    }

    private fun addTagToDeck(deckId: UUID, tagId: UUID) {
        jdbcTemplate.update(
            "INSERT INTO deck_tags (deck_id, tag_id, created_at) VALUES (?, ?, NOW())",
            deckId, tagId
        )
    }
}
