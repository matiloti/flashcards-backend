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
    fun `deck with lastStudiedAt set returns correct value`() {
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

        // Set last_studied_at directly on the deck (as happens when session completes)
        val studiedTime = java.sql.Timestamp.valueOf("2026-01-26 14:30:00")
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            studiedTime, deckId
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

        // Set last_studied_at for deck 1 (simulating completed session)
        val sessionTime = java.sql.Timestamp.valueOf("2026-01-26 12:00:00")
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            sessionTime, deckId1
        )

        // Deck 3 has no last_studied_at set (simulating only incomplete sessions or never studied)

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

    // ==================== GET /api/v1/decks/recent Tests ====================

    @Test
    fun `get recent decks returns empty list when no decks have been studied`() {
        // Create some decks without any completed study sessions
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Never Studied 1"}""")
        )
        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Never Studied 2"}""")
        )

        mockMvc.perform(get("/api/v1/decks/recent"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `get recent decks returns studied decks ordered by lastStudiedAt desc`() {
        // Create 3 decks
        val deck1Result = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Oldest Studied"}""")
        ).andReturn()
        val deck1Id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(deck1Result.response.contentAsString)["id"].asText()

        val deck2Result = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Most Recent"}""")
        ).andReturn()
        val deck2Id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(deck2Result.response.contentAsString)["id"].asText()

        val deck3Result = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Middle Studied"}""")
        ).andReturn()
        val deck3Id = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(deck3Result.response.contentAsString)["id"].asText()

        // Create cards for each deck (required for study sessions)
        jdbcTemplate.update(
            "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q', 'A')",
            deck1Id
        )
        jdbcTemplate.update(
            "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q', 'A')",
            deck2Id
        )
        jdbcTemplate.update(
            "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q', 'A')",
            deck3Id
        )

        // Update decks with last_studied_at timestamps directly
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            java.sql.Timestamp.valueOf("2026-01-24 10:00:00"), deck1Id
        )
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            java.sql.Timestamp.valueOf("2026-01-26 14:00:00"), deck2Id
        )
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            java.sql.Timestamp.valueOf("2026-01-25 12:00:00"), deck3Id
        )

        // Get recent decks - should return in order: Most Recent, Middle, Oldest
        mockMvc.perform(get("/api/v1/decks/recent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].name").value("Most Recent"))
            .andExpect(jsonPath("$[1].name").value("Middle Studied"))
            .andExpect(jsonPath("$[2].name").value("Oldest Studied"))
    }

    @Test
    fun `get recent decks with limit returns only specified number of decks`() {
        // Create 4 decks with study history
        for (i in 1..4) {
            val result = mockMvc.perform(
                post("/api/v1/decks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name": "Deck $i"}""")
            ).andReturn()
            val deckId = com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

            // Add card and study session
            jdbcTemplate.update(
                "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q', 'A')",
                deckId
            )
            jdbcTemplate.update(
                "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
                java.sql.Timestamp.valueOf("2026-01-2${i} 10:00:00"), deckId
            )
        }

        mockMvc.perform(get("/api/v1/decks/recent?limit=2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `get recent decks default limit is 3`() {
        // Create 5 decks with study history
        for (i in 1..5) {
            val result = mockMvc.perform(
                post("/api/v1/decks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name": "Deck $i"}""")
            ).andReturn()
            val deckId = com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

            jdbcTemplate.update(
                "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q', 'A')",
                deckId
            )
            jdbcTemplate.update(
                "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
                java.sql.Timestamp.valueOf("2026-01-2${i} 10:00:00"), deckId
            )
        }

        mockMvc.perform(get("/api/v1/decks/recent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `get recent decks with limit exceeding 10 returns 400`() {
        mockMvc.perform(get("/api/v1/decks/recent?limit=11"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get recent decks with limit less than 1 returns 400`() {
        mockMvc.perform(get("/api/v1/decks/recent?limit=0"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get recent decks excludes decks without lastStudiedAt`() {
        // Create 2 studied decks and 1 unstudied deck
        val studiedResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Studied Deck"}""")
        ).andReturn()
        val studiedId = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(studiedResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Never Studied Deck"}""")
        )

        // Only add study session to one deck
        jdbcTemplate.update(
            "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q', 'A')",
            studiedId
        )
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            java.sql.Timestamp.valueOf("2026-01-26 10:00:00"), studiedId
        )

        mockMvc.perform(get("/api/v1/decks/recent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Studied Deck"))
    }

    @Test
    fun `get recent decks returns correct fields`() {
        val result = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test Deck", "type": "FLASH_REVIEW"}""")
        ).andReturn()
        val deckId = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.response.contentAsString)["id"].asText()

        // Add 3 cards
        for (j in 1..3) {
            jdbcTemplate.update(
                "INSERT INTO cards (id, deck_id, front_text, back_text) VALUES (gen_random_uuid(), ?::uuid, 'Q$j', 'A$j')",
                deckId
            )
        }

        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            java.sql.Timestamp.valueOf("2026-01-26 10:00:00"), deckId
        )

        mockMvc.perform(get("/api/v1/decks/recent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(deckId))
            .andExpect(jsonPath("$[0].name").value("Test Deck"))
            .andExpect(jsonPath("$[0].type").value("FLASH_REVIEW"))
            .andExpect(jsonPath("$[0].cardCount").value(3))
            .andExpect(jsonPath("$[0].lastStudiedAt").exists())
            // Should NOT include createdAt and updatedAt per API spec
            .andExpect(jsonPath("$[0].createdAt").doesNotExist())
            .andExpect(jsonPath("$[0].updatedAt").doesNotExist())
    }
}
