package com.flashcards.deck

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class DeckTagsIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()
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
    // PATCH /api/v1/decks/{deckId}/tags tests
    // =========================================================================

    @Test
    fun `PATCH deck tags assigns tags to deck`() {
        val deckId = createDeck("TestDeck")
        val tag1Id = createTag("Biology")
        val tag2Id = createTag("Chemistry")

        mockMvc.perform(
            patch("/api/v1/decks/$deckId/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds": ["$tag1Id", "$tag2Id"]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(deckId.toString()))
            .andExpect(jsonPath("$.tags.length()").value(2))
    }

    @Test
    fun `PATCH deck tags replaces existing tags`() {
        val deckId = createDeck("TestDeck")
        val tag1Id = createTag("Biology")
        val tag2Id = createTag("Chemistry")
        val tag3Id = createTag("Physics")

        // Assign initial tags
        addTagToDeck(deckId, tag1Id)
        addTagToDeck(deckId, tag2Id)

        // Replace with new tags
        mockMvc.perform(
            patch("/api/v1/decks/$deckId/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds": ["$tag3Id"]}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tags.length()").value(1))
            .andExpect(jsonPath("$.tags[0].id").value(tag3Id.toString()))
    }

    @Test
    fun `PATCH deck tags with empty array removes all tags`() {
        val deckId = createDeck("TestDeck")
        val tagId = createTag("Biology")
        addTagToDeck(deckId, tagId)

        mockMvc.perform(
            patch("/api/v1/decks/$deckId/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds": []}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tags.length()").value(0))
    }

    @Test
    fun `PATCH deck tags returns 404 for non-existent deck`() {
        val nonExistentId = UUID.randomUUID()
        val tagId = createTag("Biology")

        mockMvc.perform(
            patch("/api/v1/decks/$nonExistentId/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds": ["$tagId"]}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH deck tags returns 400 for too many tags`() {
        val deckId = createDeck("TestDeck")
        val tagIds = (1..6).map { createTag("Tag$it") }

        mockMvc.perform(
            patch("/api/v1/decks/$deckId/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds": ${tagIds.map { "\"$it\"" }}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("TOO_MANY_TAGS"))
            .andExpect(jsonPath("$.field").value("tagIds"))
            .andExpect(jsonPath("$.max").value(5))
    }

    @Test
    fun `PATCH deck tags returns 400 for invalid tag ID`() {
        val deckId = createDeck("TestDeck")
        val invalidTagId = UUID.randomUUID()

        mockMvc.perform(
            patch("/api/v1/decks/$deckId/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"tagIds": ["$invalidTagId"]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_TAG_IDS"))
    }

    // =========================================================================
    // GET /api/v1/decks tests (with tags)
    // =========================================================================

    @Test
    fun `GET decks includes tags in response`() {
        val deckId = createDeck("TestDeck")
        val tagId = createTag("Biology")
        addTagToDeck(deckId, tagId)

        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].tags").isArray)
            .andExpect(jsonPath("$[0].tags.length()").value(1))
            .andExpect(jsonPath("$[0].tags[0].id").value(tagId.toString()))
            .andExpect(jsonPath("$[0].tags[0].name").value("Biology"))
    }

    @Test
    fun `GET decks with tagId filter returns only tagged decks`() {
        val deck1Id = createDeck("Deck1")
        val deck2Id = createDeck("Deck2")
        val tagId = createTag("Biology")
        addTagToDeck(deck1Id, tagId)

        mockMvc.perform(get("/api/v1/decks?tagId=$tagId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(deck1Id.toString()))
    }

    @Test
    fun `GET decks with untagged filter returns only untagged decks`() {
        val deck1Id = createDeck("TaggedDeck")
        val deck2Id = createDeck("UntaggedDeck")
        val tagId = createTag("Biology")
        addTagToDeck(deck1Id, tagId)

        mockMvc.perform(get("/api/v1/decks?untagged=true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(deck2Id.toString()))
    }

    @Test
    fun `GET decks with both tagId and untagged returns 400`() {
        val tagId = createTag("Biology")

        mockMvc.perform(get("/api/v1/decks?tagId=$tagId&untagged=true"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("CONFLICTING_FILTERS"))
    }

    // =========================================================================
    // GET /api/v1/decks/{deckId} tests (with tags)
    // =========================================================================

    @Test
    fun `GET deck by ID includes tags`() {
        val deckId = createDeck("TestDeck")
        val tag1Id = createTag("Biology")
        val tag2Id = createTag("Chemistry")
        addTagToDeck(deckId, tag1Id)
        addTagToDeck(deckId, tag2Id)

        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.tags").isArray)
            .andExpect(jsonPath("$.tags.length()").value(2))
    }

    // =========================================================================
    // POST /api/v1/decks tests (with tags)
    // =========================================================================

    @Test
    fun `POST deck with tagIds creates deck with tags`() {
        val tag1Id = createTag("Biology")
        val tag2Id = createTag("Chemistry")

        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Deck", "tagIds": ["$tag1Id", "$tag2Id"]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("New Deck"))
            .andExpect(jsonPath("$.tags.length()").value(2))
    }

    @Test
    fun `POST deck without tagIds creates deck with empty tags`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Deck"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.tags").isArray)
            .andExpect(jsonPath("$.tags.length()").value(0))
    }

    @Test
    fun `POST deck with invalid tagIds returns 400`() {
        val invalidTagId = UUID.randomUUID()

        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Deck", "tagIds": ["$invalidTagId"]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_TAG_IDS"))
    }

    @Test
    fun `POST deck with too many tags returns 400`() {
        val tagIds = (1..6).map { createTag("Tag$it") }

        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Deck", "tagIds": ${tagIds.map { "\"$it\"" }}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("TOO_MANY_TAGS"))
            .andExpect(jsonPath("$.field").value("tagIds"))
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
