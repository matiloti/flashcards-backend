package com.flashcards.tag

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
class TagServiceTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var tagService: TagService

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
    // getAllTags tests
    // =========================================================================

    @Test
    fun `getAllTags returns empty list when no tags exist`() {
        val tags = tagService.getAllTags(sentinelUserId)
        assertThat(tags).isEmpty()
    }

    @Test
    fun `getAllTags returns tags with correct deck counts`() {
        val tag1Id = createTag("Tag1")
        val tag2Id = createTag("Tag2")
        val deck1Id = createDeck("Deck1")
        val deck2Id = createDeck("Deck2")

        addTagToDeck(deck1Id, tag1Id)
        addTagToDeck(deck2Id, tag1Id)
        addTagToDeck(deck1Id, tag2Id)

        val tags = tagService.getAllTags(sentinelUserId)

        assertThat(tags).hasSize(2)
        val tag1 = tags.find { it.name == "Tag1" }
        val tag2 = tags.find { it.name == "Tag2" }
        assertThat(tag1!!.deckCount).isEqualTo(2)
        assertThat(tag2!!.deckCount).isEqualTo(1)
    }

    // =========================================================================
    // getTagsPaginated tests
    // =========================================================================

    @Test
    fun `getTagsPaginated returns paginated response`() {
        createTag("Tag1")
        createTag("Tag2")
        createTag("Tag3")

        val result = tagService.getTagsPaginated(sentinelUserId, 0, 2, "name,asc")

        assertThat(result.content).hasSize(2)
        assertThat(result.totalElements).isEqualTo(3L)
        assertThat(result.page).isEqualTo(0)
        assertThat(result.size).isEqualTo(2)
    }

    @Test
    fun `getTagsPaginated returns empty list when no tags exist`() {
        val result = tagService.getTagsPaginated(sentinelUserId, 0, 50, "name,asc")

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(0L)
        assertThat(result.page).isEqualTo(0)
        assertThat(result.size).isEqualTo(50)
    }

    @Test
    fun `getTagsPaginated parses sort parameter correctly`() {
        createTag("Zebra")
        createTag("Alpha")
        createTag("Beta")

        val resultAsc = tagService.getTagsPaginated(sentinelUserId, 0, 10, "name,asc")
        val resultDesc = tagService.getTagsPaginated(sentinelUserId, 0, 10, "name,desc")

        assertThat(resultAsc.content[0].name).isEqualTo("Alpha")
        assertThat(resultDesc.content[0].name).isEqualTo("Zebra")
    }

    // =========================================================================
    // getTagById tests
    // =========================================================================

    @Test
    fun `getTagById returns tag when found`() {
        val tagId = createTag("TestTag")
        val deckId = createDeck("Deck1")
        addTagToDeck(deckId, tagId)

        val tag = tagService.getTagById(sentinelUserId, tagId)

        assertThat(tag.id).isEqualTo(tagId)
        assertThat(tag.name).isEqualTo("TestTag")
        assertThat(tag.deckCount).isEqualTo(1)
    }

    @Test
    fun `getTagById throws TagNotFoundException when not found`() {
        val nonExistentId = UUID.randomUUID()

        assertThatThrownBy { tagService.getTagById(sentinelUserId, nonExistentId) }
            .isInstanceOf(TagNotFoundException::class.java)
    }

    @Test
    fun `getTagById throws TagNotFoundException when tag belongs to different user`() {
        val otherUserId = UUID.randomUUID()
        val tagId = createTagForUser("OtherTag", otherUserId)

        assertThatThrownBy { tagService.getTagById(sentinelUserId, tagId) }
            .isInstanceOf(TagNotFoundException::class.java)
    }

    // =========================================================================
    // createTag tests
    // =========================================================================

    @Test
    fun `createTag creates tag with trimmed name`() {
        val request = CreateTagRequest(name = "  Biology  ")

        val tag = tagService.createTag(sentinelUserId, request)

        assertThat(tag.name).isEqualTo("Biology")
        assertThat(tag.deckCount).isEqualTo(0)
        assertThat(tag.createdAt).isNotNull()
    }

    @Test
    fun `createTag throws DuplicateTagNameException for duplicate name`() {
        createTag("Biology")

        val request = CreateTagRequest(name = "Biology")

        assertThatThrownBy { tagService.createTag(sentinelUserId, request) }
            .isInstanceOf(DuplicateTagNameException::class.java)
    }

    @Test
    fun `createTag throws DuplicateTagNameException for case-insensitive duplicate`() {
        createTag("Biology")

        val request = CreateTagRequest(name = "BIOLOGY")

        assertThatThrownBy { tagService.createTag(sentinelUserId, request) }
            .isInstanceOf(DuplicateTagNameException::class.java)
    }

    @Test
    fun `createTag throws IllegalArgumentException for blank name`() {
        val request = CreateTagRequest(name = "   ")

        assertThatThrownBy { tagService.createTag(sentinelUserId, request) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("blank")
    }

    @Test
    fun `createTag throws IllegalArgumentException for name exceeding 30 chars`() {
        val longName = "a".repeat(31)
        val request = CreateTagRequest(name = longName)

        assertThatThrownBy { tagService.createTag(sentinelUserId, request) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("30")
    }

    // =========================================================================
    // updateTag tests
    // =========================================================================

    @Test
    fun `updateTag updates tag name`() {
        val tagId = createTag("OldName")
        val deckId = createDeck("Deck1")
        addTagToDeck(deckId, tagId)

        val request = UpdateTagRequest(name = "NewName")
        val updated = tagService.updateTag(sentinelUserId, tagId, request)

        assertThat(updated.name).isEqualTo("NewName")
        assertThat(updated.deckCount).isEqualTo(1)
    }

    @Test
    fun `updateTag throws TagNotFoundException when tag not found`() {
        val nonExistentId = UUID.randomUUID()
        val request = UpdateTagRequest(name = "NewName")

        assertThatThrownBy { tagService.updateTag(sentinelUserId, nonExistentId, request) }
            .isInstanceOf(TagNotFoundException::class.java)
    }

    @Test
    fun `updateTag throws DuplicateTagNameException when name conflicts with other tag`() {
        createTag("ExistingTag")
        val tagId = createTag("TagToUpdate")

        val request = UpdateTagRequest(name = "ExistingTag")

        assertThatThrownBy { tagService.updateTag(sentinelUserId, tagId, request) }
            .isInstanceOf(DuplicateTagNameException::class.java)
    }

    @Test
    fun `updateTag allows updating to same name with different case`() {
        val tagId = createTag("biology")

        val request = UpdateTagRequest(name = "Biology")
        val updated = tagService.updateTag(sentinelUserId, tagId, request)

        assertThat(updated.name).isEqualTo("Biology")
    }

    // =========================================================================
    // deleteTag tests
    // =========================================================================

    @Test
    fun `deleteTag removes tag`() {
        val tagId = createTag("ToDelete")

        tagService.deleteTag(sentinelUserId, tagId)

        assertThatThrownBy { tagService.getTagById(sentinelUserId, tagId) }
            .isInstanceOf(TagNotFoundException::class.java)
    }

    @Test
    fun `deleteTag throws TagNotFoundException when tag not found`() {
        val nonExistentId = UUID.randomUUID()

        assertThatThrownBy { tagService.deleteTag(sentinelUserId, nonExistentId) }
            .isInstanceOf(TagNotFoundException::class.java)
    }

    @Test
    fun `deleteTag removes deck associations but not decks`() {
        val tagId = createTag("TagToDelete")
        val deckId = createDeck("TestDeck")
        addTagToDeck(deckId, tagId)

        tagService.deleteTag(sentinelUserId, tagId)

        // Deck should still exist
        val deckCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM decks WHERE id = ?",
            Int::class.java,
            deckId
        )
        assertThat(deckCount).isEqualTo(1)
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private fun createTag(name: String): UUID {
        return createTagForUser(name, sentinelUserId)
    }

    private fun createTagForUser(name: String, userId: UUID): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO tags (id, name, user_id, created_at) VALUES (?, ?, ?, ?)",
            id, name, userId, java.sql.Timestamp.from(now)
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
