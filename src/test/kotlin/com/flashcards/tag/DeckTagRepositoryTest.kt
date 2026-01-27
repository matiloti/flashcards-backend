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
class DeckTagRepositoryTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var deckTagRepository: DeckTagRepository

    private val sentinelUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM deck_tags")
        jdbcTemplate.execute("DELETE FROM tags")
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM card_progress")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
    }

    // =========================================================================
    // findTagsByDeckId tests
    // =========================================================================

    @Test
    fun `findTagsByDeckId returns empty list when deck has no tags`() {
        val deckId = createDeck("NoTagsDeck")

        val tags = deckTagRepository.findTagsByDeckId(deckId)

        assertThat(tags).isEmpty()
    }

    @Test
    fun `findTagsByDeckId returns all tags for deck`() {
        val deckId = createDeck("TaggedDeck")
        val tag1Id = createTag("Biology")
        val tag2Id = createTag("Chemistry")
        addTagToDeck(deckId, tag1Id)
        addTagToDeck(deckId, tag2Id)

        val tags = deckTagRepository.findTagsByDeckId(deckId)

        assertThat(tags).hasSize(2)
        assertThat(tags.map { it.name }).containsExactlyInAnyOrder("Biology", "Chemistry")
    }

    @Test
    fun `findTagsByDeckId returns tags ordered by name`() {
        val deckId = createDeck("TaggedDeck")
        val tag1Id = createTag("Zebra")
        val tag2Id = createTag("Apple")
        val tag3Id = createTag("Mango")
        addTagToDeck(deckId, tag1Id)
        addTagToDeck(deckId, tag2Id)
        addTagToDeck(deckId, tag3Id)

        val tags = deckTagRepository.findTagsByDeckId(deckId)

        assertThat(tags).hasSize(3)
        assertThat(tags[0].name).isEqualTo("Apple")
        assertThat(tags[1].name).isEqualTo("Mango")
        assertThat(tags[2].name).isEqualTo("Zebra")
    }

    // =========================================================================
    // setTagsForDeck tests
    // =========================================================================

    @Test
    fun `setTagsForDeck adds tags to deck`() {
        val deckId = createDeck("TestDeck")
        val tag1Id = createTag("Tag1")
        val tag2Id = createTag("Tag2")

        deckTagRepository.setTagsForDeck(deckId, listOf(tag1Id, tag2Id))

        val tags = deckTagRepository.findTagsByDeckId(deckId)
        assertThat(tags).hasSize(2)
        assertThat(tags.map { it.id }).containsExactlyInAnyOrder(tag1Id, tag2Id)
    }

    @Test
    fun `setTagsForDeck replaces existing tags`() {
        val deckId = createDeck("TestDeck")
        val tag1Id = createTag("Tag1")
        val tag2Id = createTag("Tag2")
        val tag3Id = createTag("Tag3")

        // Add initial tags
        addTagToDeck(deckId, tag1Id)
        addTagToDeck(deckId, tag2Id)

        // Replace with different tags
        deckTagRepository.setTagsForDeck(deckId, listOf(tag2Id, tag3Id))

        val tags = deckTagRepository.findTagsByDeckId(deckId)
        assertThat(tags).hasSize(2)
        assertThat(tags.map { it.id }).containsExactlyInAnyOrder(tag2Id, tag3Id)
    }

    @Test
    fun `setTagsForDeck with empty list removes all tags`() {
        val deckId = createDeck("TestDeck")
        val tag1Id = createTag("Tag1")
        addTagToDeck(deckId, tag1Id)

        deckTagRepository.setTagsForDeck(deckId, emptyList())

        val tags = deckTagRepository.findTagsByDeckId(deckId)
        assertThat(tags).isEmpty()
    }

    @Test
    fun `setTagsForDeck handles duplicate tag IDs gracefully`() {
        val deckId = createDeck("TestDeck")
        val tagId = createTag("Tag1")

        // Pass same tag ID twice
        deckTagRepository.setTagsForDeck(deckId, listOf(tagId, tagId))

        val tags = deckTagRepository.findTagsByDeckId(deckId)
        assertThat(tags).hasSize(1)
    }

    // =========================================================================
    // countByTagId tests
    // =========================================================================

    @Test
    fun `countByTagId returns 0 for unused tag`() {
        val tagId = createTag("UnusedTag")

        val count = deckTagRepository.countByTagId(tagId)

        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `countByTagId returns correct count`() {
        val tagId = createTag("PopularTag")
        val deck1Id = createDeck("Deck1")
        val deck2Id = createDeck("Deck2")
        val deck3Id = createDeck("Deck3")

        addTagToDeck(deck1Id, tagId)
        addTagToDeck(deck2Id, tagId)

        val count = deckTagRepository.countByTagId(tagId)

        assertThat(count).isEqualTo(2)
    }

    // =========================================================================
    // deleteByDeckId tests
    // =========================================================================

    @Test
    fun `deleteByDeckId removes all tags from deck`() {
        val deckId = createDeck("TestDeck")
        val tag1Id = createTag("Tag1")
        val tag2Id = createTag("Tag2")
        addTagToDeck(deckId, tag1Id)
        addTagToDeck(deckId, tag2Id)

        deckTagRepository.deleteByDeckId(deckId)

        val tags = deckTagRepository.findTagsByDeckId(deckId)
        assertThat(tags).isEmpty()

        // Tags themselves should still exist
        val tagCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tags",
            Int::class.java
        )
        assertThat(tagCount).isEqualTo(2)
    }

    // =========================================================================
    // findTagsForDecks (batch loading) tests
    // =========================================================================

    @Test
    fun `findTagsForDecks returns empty map for empty deck list`() {
        val result = deckTagRepository.findTagsForDecks(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `findTagsForDecks returns tags grouped by deck`() {
        val deck1Id = createDeck("Deck1")
        val deck2Id = createDeck("Deck2")
        val deck3Id = createDeck("Deck3") // No tags

        val tag1Id = createTag("Tag1")
        val tag2Id = createTag("Tag2")
        val tag3Id = createTag("Tag3")

        addTagToDeck(deck1Id, tag1Id)
        addTagToDeck(deck1Id, tag2Id)
        addTagToDeck(deck2Id, tag3Id)

        val result = deckTagRepository.findTagsForDecks(listOf(deck1Id, deck2Id, deck3Id))

        assertThat(result).hasSize(2) // Only decks with tags
        assertThat(result[deck1Id]).hasSize(2)
        assertThat(result[deck2Id]).hasSize(1)
        assertThat(result[deck3Id]).isNull()
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private fun createTag(name: String): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO tags (id, name, user_id, created_at) VALUES (?, ?, ?, ?)",
            id, name, sentinelUserId, java.sql.Timestamp.from(now)
        )
        return id
    }

    private fun createDeck(name: String): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO decks (id, name, deck_type, created_at, updated_at) VALUES (?, ?, 'STUDY', ?, ?)",
            id, name, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
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
