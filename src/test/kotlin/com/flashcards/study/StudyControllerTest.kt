package com.flashcards.study

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class StudyControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()

    private lateinit var deckId: String
    private lateinit var cardId: String

    private val sentinelUserId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setup() {
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM card_progress")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
        jdbcTemplate.execute("DELETE FROM daily_study_stats")
        jdbcTemplate.execute("DELETE FROM user_statistics")

        // Ensure sentinel user_statistics row exists
        jdbcTemplate.update(
            """INSERT INTO user_statistics (id, user_id, current_streak, longest_streak, total_cards_studied,
               total_study_time_minutes, total_sessions) VALUES (?, ?, 0, 0, 0, 0, 0)
               ON CONFLICT (user_id) DO NOTHING""",
            sentinelUserId, sentinelUserId
        )

        // Create a deck
        val deckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Study Deck"}""")
        ).andReturn()
        deckId = objectMapper.readTree(deckResult.response.contentAsString)["id"].asText()

        // Create a card
        val cardResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q1", "backText": "A1"}""")
        ).andReturn()
        cardId = objectMapper.readTree(cardResult.response.contentAsString)["id"].asText()
    }

    @Test
    fun `start session returns 201 with cards`() {
        mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.sessionId").isNotEmpty)
            .andExpect(jsonPath("$.deckName").value("Study Deck"))
            .andExpect(jsonPath("$.totalCards").value(1))
            .andExpect(jsonPath("$.cards[0].frontText").value("Q1"))
    }

    @Test
    fun `start session for non-existent deck returns 404`() {
        mockMvc.perform(
            post("/api/v1/decks/00000000-0000-0000-0000-000000000000/study")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `start session for empty deck returns 400`() {
        // Create empty deck
        val emptyDeckResult = mockMvc.perform(
            post("/api/v1/decks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Empty Deck"}""")
        ).andReturn()
        val emptyDeckId = objectMapper.readTree(emptyDeckResult.response.contentAsString)["id"].asText()

        mockMvc.perform(
            post("/api/v1/decks/$emptyDeckId/study")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `submit review returns 201`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.rating").value("EASY"))
            .andExpect(jsonPath("$.cardId").value(cardId))
    }

    @Test
    fun `submit review for non-existent session returns 404`() {
        mockMvc.perform(
            post("/api/v1/study/00000000-0000-0000-0000-000000000000/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "HARD"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `complete session returns summary`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit review
        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )

        // Complete session
        mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sessionId").value(sessionId))
            .andExpect(jsonPath("$.deckName").value("Study Deck"))
            .andExpect(jsonPath("$.easyCount").value(1))
            .andExpect(jsonPath("$.hardCount").value(0))
            .andExpect(jsonPath("$.againCount").value(0))
            .andExpect(jsonPath("$.totalCards").value(1))
    }

    @Test
    fun `complete non-existent session returns 404`() {
        mockMvc.perform(
            post("/api/v1/study/00000000-0000-0000-0000-000000000000/complete")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `complete session updates deck lastStudiedAt`() {
        // Verify deck has null lastStudiedAt initially
        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value(null as Any?))

        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit review
        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )

        // Complete session
        val completeResult = mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)
            .andReturn()

        val completedAt = objectMapper.readTree(completeResult.response.contentAsString)["completedAt"].asText()

        // Verify deck now has lastStudiedAt set to completedAt
        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value(completedAt))
    }

    @Test
    fun `complete session updates deck lastStudiedAt to most recent time`() {
        // Set initial lastStudiedAt
        val oldTime = java.sql.Timestamp.valueOf("2026-01-01 10:00:00")
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?::uuid",
            oldTime, deckId
        )

        // Start and complete new session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )

        val completeResult = mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)
            .andReturn()

        val completedAt = objectMapper.readTree(completeResult.response.contentAsString)["completedAt"].asText()

        // Verify deck lastStudiedAt is updated to new completedAt
        mockMvc.perform(get("/api/v1/decks/$deckId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lastStudiedAt").value(completedAt))
    }

    // =========================================================================
    // Statistics tracking tests
    // =========================================================================

    @Test
    fun `complete session updates user_statistics totals`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit review
        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )

        // Complete session
        mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)

        // Verify user_statistics was updated
        val stats = jdbcTemplate.queryForMap(
            "SELECT total_cards_studied, total_sessions FROM user_statistics WHERE id = ?",
            sentinelUserId
        )

        org.assertj.core.api.Assertions.assertThat(stats["total_cards_studied"]).isEqualTo(1)
        org.assertj.core.api.Assertions.assertThat(stats["total_sessions"]).isEqualTo(1)
    }

    @Test
    fun `complete session creates daily_study_stats entry`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit reviews with different ratings
        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )

        // Complete session
        mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)

        // Verify daily_study_stats was created for today
        val today = java.time.LocalDate.now()
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_study_stats WHERE study_date = ?",
            Int::class.java,
            java.sql.Date.valueOf(today)
        )

        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1)
    }

    @Test
    fun `submit review updates card_progress`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit EASY review
        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )
            .andExpect(status().isCreated)

        // Verify card_progress was created/updated
        val progress = jdbcTemplate.queryForMap(
            "SELECT consecutive_easy_count, mastery_level, last_rating FROM card_progress WHERE card_id = ?::uuid",
            cardId
        )

        org.assertj.core.api.Assertions.assertThat(progress["consecutive_easy_count"]).isEqualTo(1)
        org.assertj.core.api.Assertions.assertThat(progress["last_rating"]).isEqualTo("EASY")
        org.assertj.core.api.Assertions.assertThat(progress["mastery_level"]).isEqualTo("LEARNING")
    }

    @Test
    fun `consecutive EASY ratings leads to MASTERED status`() {
        // Create multiple cards
        mockMvc.perform(
            post("/api/v1/decks/$deckId/cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"frontText": "Q2", "backText": "A2"}""")
        )

        // Do 3 sessions with EASY rating for the first card
        repeat(3) {
            val sessionResult = mockMvc.perform(
                post("/api/v1/decks/$deckId/study")
            ).andReturn()
            val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

            // Submit EASY for the first card
            mockMvc.perform(
                post("/api/v1/study/$sessionId/reviews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"cardId": "$cardId", "rating": "EASY"}""")
            )

            mockMvc.perform(
                post("/api/v1/study/$sessionId/complete")
            )
        }

        // Verify card is now MASTERED
        val masteryLevel = jdbcTemplate.queryForObject(
            "SELECT mastery_level FROM card_progress WHERE card_id = ?::uuid",
            String::class.java,
            cardId
        )

        org.assertj.core.api.Assertions.assertThat(masteryLevel).isEqualTo("MASTERED")
    }

    @Test
    fun `HARD rating resets consecutive_easy_count`() {
        // Submit 2 EASY ratings
        repeat(2) {
            val sessionResult = mockMvc.perform(
                post("/api/v1/decks/$deckId/study")
            ).andReturn()
            val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

            mockMvc.perform(
                post("/api/v1/study/$sessionId/reviews")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"cardId": "$cardId", "rating": "EASY"}""")
            )
            mockMvc.perform(post("/api/v1/study/$sessionId/complete"))
        }

        // Now submit HARD
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "HARD"}""")
        )

        // Verify consecutive_easy_count is reset to 0
        val count = jdbcTemplate.queryForObject(
            "SELECT consecutive_easy_count FROM card_progress WHERE card_id = ?::uuid",
            Int::class.java,
            cardId
        )

        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0)
    }

    @Test
    fun `statistics are visible via statistics API after session`() {
        // Start session
        val sessionResult = mockMvc.perform(
            post("/api/v1/decks/$deckId/study")
        ).andReturn()
        val sessionId = objectMapper.readTree(sessionResult.response.contentAsString)["sessionId"].asText()

        // Submit review
        mockMvc.perform(
            post("/api/v1/study/$sessionId/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId": "$cardId", "rating": "EASY"}""")
        )

        // Complete session
        mockMvc.perform(
            post("/api/v1/study/$sessionId/complete")
        )
            .andExpect(status().isOk)

        // Verify statistics API shows the data
        mockMvc.perform(get("/api/v1/statistics/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.allTime.totalCardsStudied").value(1))
            .andExpect(jsonPath("$.allTime.totalSessions").value(1))
            .andExpect(jsonPath("$.today.cardsStudied").value(1))
            .andExpect(jsonPath("$.today.sessionsCompleted").value(1))
    }
}
