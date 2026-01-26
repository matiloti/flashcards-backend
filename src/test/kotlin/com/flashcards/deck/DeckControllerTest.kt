package com.flashcards.deck

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class DeckControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
    }

    @Test
    fun `list decks returns empty list when no decks exist`() {
        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `create deck returns 201 with deck`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Biology 101"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Biology 101"))
            .andExpect(jsonPath("$.cardCount").value(0))
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `create deck with blank name returns 400`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "   "}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `create deck with name exceeding 100 chars returns 400`() {
        val longName = "a".repeat(101)
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$longName"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get deck by id returns deck`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test Deck"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(get("/api/v1/decks/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Test Deck"))
    }

    @Test
    fun `get non-existent deck returns 404`() {
        mockMvc.perform(get("/api/v1/decks/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `update deck returns updated deck`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Old Name"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            put("/api/v1/decks/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Name"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New Name"))
    }

    @Test
    fun `delete deck returns 204`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "To Delete"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(delete("/api/v1/decks/$id"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `delete non-existent deck returns 404`() {
        mockMvc.perform(delete("/api/v1/decks/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `list decks returns created decks`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Deck 1"}""")
        )
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Deck 2"}""")
        )

        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `create deck without type defaults to STUDY`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Default Type Deck"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Default Type Deck"))
            .andExpect(jsonPath("$.type").value("STUDY"))
    }

    @Test
    fun `create deck with explicit STUDY type`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Study Deck", "type": "STUDY"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Study Deck"))
            .andExpect(jsonPath("$.type").value("STUDY"))
    }

    @Test
    fun `create deck with FLASH_REVIEW type`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Flash Review Deck", "type": "FLASH_REVIEW"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Flash Review Deck"))
            .andExpect(jsonPath("$.type").value("FLASH_REVIEW"))
    }

    @Test
    fun `get deck includes type field`() {
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Typed Deck", "type": "FLASH_REVIEW"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        mockMvc.perform(get("/api/v1/decks/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("FLASH_REVIEW"))
    }

    @Test
    fun `list decks includes type field`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Study", "type": "STUDY"}""")
        )
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Flash", "type": "FLASH_REVIEW"}""")
        )

        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name == 'Study')].type").value("STUDY"))
            .andExpect(jsonPath("$[?(@.name == 'Flash')].type").value("FLASH_REVIEW"))
    }

    // ==================== lastStudiedAt Tests ====================

    @Test
    fun `deck with completed study sessions returns lastStudiedAt as max completed_at`() {
        // Create deck
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Studied Deck"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val deckId = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        // Insert completed study sessions directly via SQL
        val session1Time = java.sql.Timestamp.valueOf("2026-01-25 10:00:00")
        val session2Time = java.sql.Timestamp.valueOf("2026-01-26 14:30:00")  // This is the MAX
        val session3Time = java.sql.Timestamp.valueOf("2026-01-24 08:00:00")

        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, started_at, completed_at) VALUES (gen_random_uuid(), ?::uuid, NOW(), ?)",
            deckId, session1Time
        )
        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, started_at, completed_at) VALUES (gen_random_uuid(), ?::uuid, NOW(), ?)",
            deckId, session2Time
        )
        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, started_at, completed_at) VALUES (gen_random_uuid(), ?::uuid, NOW(), ?)",
            deckId, session3Time
        )

        // Verify GET by ID returns correct lastStudiedAt
        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value("2026-01-26T13:30:00Z"))  // UTC conversion

        // Verify list also returns correct lastStudiedAt
        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].lastStudiedAt").value("2026-01-26T13:30:00Z"))
    }

    @Test
    fun `deck with no study sessions returns null lastStudiedAt`() {
        // Create deck without any study sessions
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Never Studied Deck"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val deckId = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        // Verify GET by ID returns null lastStudiedAt
        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value(null as Any?))

        // Verify list also returns null lastStudiedAt
        mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].lastStudiedAt").value(null as Any?))
    }

    @Test
    fun `deck with only incomplete sessions returns null lastStudiedAt`() {
        // Create deck
        val createResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Incomplete Sessions Deck"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val deckId = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(createResult.response.contentAsString)["id"].asText()

        // Insert incomplete study sessions (completed_at IS NULL)
        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, started_at, completed_at) VALUES (gen_random_uuid(), ?::uuid, NOW(), NULL)",
            deckId
        )
        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, started_at, completed_at) VALUES (gen_random_uuid(), ?::uuid, NOW(), NULL)",
            deckId
        )

        // Verify GET by ID returns null lastStudiedAt
        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value(null as Any?))
    }

    @Test
    fun `newly created deck has null lastStudiedAt`() {
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Brand New Deck"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.lastStudiedAt").value(null as Any?))
    }

    @Test
    fun `mixed decks in list have correct lastStudiedAt values`() {
        // Create deck 1: with completed sessions
        val result1 = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Deck With Sessions"}""")
        ).andReturn()
        val deckId1 = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result1.response.contentAsString)["id"].asText()

        // Create deck 2: no sessions
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Deck No Sessions"}""")
        )

        // Create deck 3: only incomplete sessions
        val result3 = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Deck Incomplete Only"}""")
        ).andReturn()
        val deckId3 = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result3.response.contentAsString)["id"].asText()

        // Add completed session to deck 1
        val sessionTime = java.sql.Timestamp.valueOf("2026-01-26 12:00:00")
        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, started_at, completed_at) VALUES (gen_random_uuid(), ?::uuid, NOW(), ?)",
            deckId1, sessionTime
        )

        // Add incomplete session to deck 3
        jdbcTemplate.update(
            "INSERT INTO study_sessions (id, deck_id, started_at, completed_at) VALUES (gen_random_uuid(), ?::uuid, NOW(), NULL)",
            deckId3
        )

        // Verify list returns correct values for all - using direct index access after verifying the data
        val response = mockMvc.perform(get("/api/v1/decks"))
            .andExpect(status().isOk)
            .andReturn()

        val responseJson = response.response.contentAsString
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        val decks = objectMapper.readTree(responseJson)

        // Find each deck and verify lastStudiedAt
        for (deck in decks) {
            when (deck["name"].asText()) {
                "Deck With Sessions" -> {
                    org.junit.jupiter.api.Assertions.assertEquals("2026-01-26T11:00:00Z", deck["lastStudiedAt"].asText())
                }
                "Deck No Sessions" -> {
                    org.junit.jupiter.api.Assertions.assertTrue(deck["lastStudiedAt"].isNull)
                }
                "Deck Incomplete Only" -> {
                    org.junit.jupiter.api.Assertions.assertTrue(deck["lastStudiedAt"].isNull)
                }
            }
        }
    }
}
