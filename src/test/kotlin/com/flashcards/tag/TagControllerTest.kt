package com.flashcards.tag

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
class TagControllerTest {

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
    // GET /api/v1/tags tests
    // =========================================================================

    @Test
    fun `GET tags returns empty list when no tags exist`() {
        mockMvc.perform(get("/api/v1/tags"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50))
    }

    @Test
    fun `GET tags returns all tags with deck counts`() {
        val tag1Id = createTag("Biology")
        val tag2Id = createTag("Chemistry")
        val deckId = createDeck("TestDeck")
        addTagToDeck(deckId, tag1Id)

        mockMvc.perform(get("/api/v1/tags"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Biology"))
            .andExpect(jsonPath("$.content[0].deckCount").value(1))
            .andExpect(jsonPath("$.content[1].name").value("Chemistry"))
            .andExpect(jsonPath("$.content[1].deckCount").value(0))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(50))
    }

    // =========================================================================
    // GET /api/v1/tags pagination tests
    // =========================================================================

    @Test
    fun `GET tags with custom page and size`() {
        // Create 5 tags
        for (i in 1..5) {
            createTag("Tag$i")
        }

        mockMvc.perform(get("/api/v1/tags?page=1&size=2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
    }

    @Test
    fun `GET tags with size exceeding max returns 400`() {
        mockMvc.perform(get("/api/v1/tags?size=101"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"))
    }

    @Test
    fun `GET tags with negative page returns 400`() {
        mockMvc.perform(get("/api/v1/tags?page=-1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"))
    }

    @Test
    fun `GET tags with size zero returns 400`() {
        mockMvc.perform(get("/api/v1/tags?size=0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"))
    }

    @Test
    fun `GET tags with sort by name desc`() {
        createTag("Alpha")
        createTag("Zeta")
        createTag("Beta")

        mockMvc.perform(get("/api/v1/tags?sort=name,desc"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].name").value("Zeta"))
            .andExpect(jsonPath("$.content[1].name").value("Beta"))
            .andExpect(jsonPath("$.content[2].name").value("Alpha"))
    }

    @Test
    fun `GET tags defaults to name asc sort`() {
        createTag("Zeta")
        createTag("Alpha")
        createTag("Beta")

        mockMvc.perform(get("/api/v1/tags"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].name").value("Alpha"))
            .andExpect(jsonPath("$.content[1].name").value("Beta"))
            .andExpect(jsonPath("$.content[2].name").value("Zeta"))
    }

    // =========================================================================
    // POST /api/v1/tags tests
    // =========================================================================

    @Test
    fun `POST tags creates new tag and returns 201`() {
        mockMvc.perform(
            post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Biology"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Biology"))
            .andExpect(jsonPath("$.deckCount").value(0))
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
    }

    @Test
    fun `POST tags trims whitespace from name`() {
        mockMvc.perform(
            post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "  Biology  "}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Biology"))
    }

    @Test
    fun `POST tags returns 400 for blank name`() {
        mockMvc.perform(
            post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "   "}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_TAG_NAME"))
    }

    @Test
    fun `POST tags returns 400 for name exceeding 30 chars`() {
        val longName = "a".repeat(31)
        mockMvc.perform(
            post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$longName"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("INVALID_TAG_NAME"))
    }

    @Test
    fun `POST tags returns 409 for duplicate name`() {
        createTag("Biology")

        mockMvc.perform(
            post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Biology"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DUPLICATE_TAG_NAME"))
            .andExpect(jsonPath("$.field").value("name"))
    }

    @Test
    fun `POST tags returns 409 for case-insensitive duplicate`() {
        createTag("Biology")

        mockMvc.perform(
            post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "BIOLOGY"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DUPLICATE_TAG_NAME"))
    }

    // =========================================================================
    // GET /api/v1/tags/{tagId} tests
    // =========================================================================

    @Test
    fun `GET tag by ID returns tag`() {
        val tagId = createTag("Biology")
        val deckId = createDeck("TestDeck")
        addTagToDeck(deckId, tagId)

        mockMvc.perform(get("/api/v1/tags/$tagId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(tagId.toString()))
            .andExpect(jsonPath("$.name").value("Biology"))
            .andExpect(jsonPath("$.deckCount").value(1))
            .andExpect(jsonPath("$.createdAt").isNotEmpty)
    }

    @Test
    fun `GET tag by ID returns 404 for non-existent tag`() {
        val nonExistentId = UUID.randomUUID()

        mockMvc.perform(get("/api/v1/tags/$nonExistentId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("TAG_NOT_FOUND"))
    }

    // =========================================================================
    // PATCH /api/v1/tags/{tagId} tests
    // =========================================================================

    @Test
    fun `PATCH tag updates name`() {
        val tagId = createTag("OldName")

        mockMvc.perform(
            patch("/api/v1/tags/$tagId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "NewName"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(tagId.toString()))
            .andExpect(jsonPath("$.name").value("NewName"))
    }

    @Test
    fun `PATCH tag returns 404 for non-existent tag`() {
        val nonExistentId = UUID.randomUUID()

        mockMvc.perform(
            patch("/api/v1/tags/$nonExistentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "NewName"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH tag returns 409 for duplicate name`() {
        createTag("ExistingTag")
        val tagId = createTag("TagToUpdate")

        mockMvc.perform(
            patch("/api/v1/tags/$tagId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "ExistingTag"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("DUPLICATE_TAG_NAME"))
    }

    @Test
    fun `PATCH tag allows updating to same name with different case`() {
        val tagId = createTag("biology")

        mockMvc.perform(
            patch("/api/v1/tags/$tagId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Biology"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Biology"))
    }

    // =========================================================================
    // DELETE /api/v1/tags/{tagId} tests
    // =========================================================================

    @Test
    fun `DELETE tag returns 204`() {
        val tagId = createTag("ToDelete")

        mockMvc.perform(delete("/api/v1/tags/$tagId"))
            .andExpect(status().isNoContent)

        // Verify tag is deleted
        mockMvc.perform(get("/api/v1/tags/$tagId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE tag returns 404 for non-existent tag`() {
        val nonExistentId = UUID.randomUUID()

        mockMvc.perform(delete("/api/v1/tags/$nonExistentId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE tag removes deck associations`() {
        val tagId = createTag("TagToDelete")
        val deckId = createDeck("TestDeck")
        addTagToDeck(deckId, tagId)

        mockMvc.perform(delete("/api/v1/tags/$tagId"))
            .andExpect(status().isNoContent)

        // Verify deck still exists
        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
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
