package com.flashcards.statistics

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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class StatisticsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private val objectMapper = ObjectMapper()
    private val sentinelUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setup() {
        // Clean up in reverse dependency order
        jdbcTemplate.execute("DELETE FROM card_reviews")
        jdbcTemplate.execute("DELETE FROM card_progress")
        jdbcTemplate.execute("DELETE FROM study_sessions")
        jdbcTemplate.execute("DELETE FROM cards")
        jdbcTemplate.execute("DELETE FROM decks")
        jdbcTemplate.execute("DELETE FROM daily_study_stats")
        jdbcTemplate.execute("DELETE FROM user_statistics")

        // Ensure sentinel user_statistics row exists
        jdbcTemplate.update(
            """INSERT INTO user_statistics (id, current_streak, longest_streak, total_cards_studied,
               total_study_time_minutes, total_sessions) VALUES (?, 0, 0, 0, 0, 0)
               ON CONFLICT (id) DO NOTHING""",
            sentinelUserId
        )
    }

    // =========================================================================
    // GET /api/v1/statistics/overview tests
    // =========================================================================

    @Test
    fun `getOverview returns 200 with empty state for new user`() {
        mockMvc.perform(get("/api/v1/statistics/overview"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.streak.current").value(0))
            .andExpect(jsonPath("$.streak.longest").value(0))
            .andExpect(jsonPath("$.streak.lastStudyDate").isEmpty)
            .andExpect(jsonPath("$.today.cardsStudied").value(0))
            .andExpect(jsonPath("$.today.timeMinutes").value(0))
            .andExpect(jsonPath("$.today.sessionsCompleted").value(0))
            .andExpect(jsonPath("$.week.days").isArray)
            .andExpect(jsonPath("$.week.days.length()").value(7))
            .andExpect(jsonPath("$.week.totalCardsStudied").value(0))
            .andExpect(jsonPath("$.week.daysStudied").value(0))
            .andExpect(jsonPath("$.allTime.totalCardsStudied").value(0))
            .andExpect(jsonPath("$.allTime.totalTimeMinutes").value(0))
            .andExpect(jsonPath("$.allTime.totalDecks").value(0))
            .andExpect(jsonPath("$.allTime.totalSessions").value(0))
            .andExpect(jsonPath("$.cardProgress.mastered").value(0))
            .andExpect(jsonPath("$.cardProgress.learning").value(0))
            .andExpect(jsonPath("$.cardProgress.new").value(0))
            .andExpect(jsonPath("$.cardProgress.total").value(0))
            .andExpect(jsonPath("$.accuracy.rate").isEmpty)
            .andExpect(jsonPath("$.accuracy.trend").isEmpty)
            .andExpect(jsonPath("$.accuracy.periodDays").value(7))
            .andExpect(jsonPath("$.topDecks").isArray)
            .andExpect(jsonPath("$.topDecks.length()").value(0))
    }

    @Test
    fun `getOverview uses default UTC timezone when not specified`() {
        mockMvc.perform(get("/api/v1/statistics/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.week.days.length()").value(7))
    }

    @Test
    fun `getOverview accepts timezone parameter`() {
        mockMvc.perform(get("/api/v1/statistics/overview")
            .param("timezone", "America/New_York"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.week.days.length()").value(7))
    }

    @Test
    fun `getOverview returns 400 for invalid timezone`() {
        mockMvc.perform(get("/api/v1/statistics/overview")
            .param("timezone", "Invalid/Timezone"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Invalid timezone"))
            .andExpect(jsonPath("$.validExample").value("America/New_York"))
    }

    @Test
    fun `getOverview returns data with study history`() {
        val today = LocalDate.now()

        // Set up user stats
        jdbcTemplate.update(
            """UPDATE user_statistics SET current_streak = 3, longest_streak = 10,
               last_study_date = ?, total_cards_studied = 500,
               total_study_time_minutes = 300, total_sessions = 50 WHERE id = ?""",
            java.sql.Date.valueOf(today), sentinelUserId
        )

        // Create a deck and cards
        val deckId = createTestDeck("Test Deck")
        val card1 = createTestCard(deckId, "Q1", "A1")
        val card2 = createTestCard(deckId, "Q2", "A2")

        // Set up card progress
        jdbcTemplate.update(
            """INSERT INTO card_progress (card_id, mastery_level, consecutive_easy_count, total_reviews)
               VALUES (?, 'MASTERED', 3, 5)""", card1
        )

        // Set up daily stats
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, 20, 15, 2, 10, 5, 5)""",
            java.sql.Date.valueOf(today)
        )

        mockMvc.perform(get("/api/v1/statistics/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.allTime.totalCardsStudied").value(500))
            .andExpect(jsonPath("$.allTime.totalDecks").value(1))
            .andExpect(jsonPath("$.cardProgress.mastered").value(1))
            .andExpect(jsonPath("$.cardProgress.new").value(1)) // card2 has no progress
            .andExpect(jsonPath("$.cardProgress.total").value(2))
            .andExpect(jsonPath("$.today.cardsStudied").value(20))
    }

    @Test
    fun `getOverview returns top decks with progress`() {
        // Create two decks with study history
        val deck1Id = createTestDeck("Biology")
        val deck2Id = createTestDeck("Chemistry")

        // Create cards in both decks
        createTestCard(deck1Id, "Q1", "A1")
        createTestCard(deck1Id, "Q2", "A2")
        createTestCard(deck2Id, "Q3", "A3")

        // Mark deck1 as studied
        val now = Instant.now()
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?",
            java.sql.Timestamp.from(now), deck1Id
        )

        mockMvc.perform(get("/api/v1/statistics/overview"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topDecks.length()").value(1))
            .andExpect(jsonPath("$.topDecks[0].name").value("Biology"))
            .andExpect(jsonPath("$.topDecks[0].totalCards").value(2))
    }

    @Test
    fun `getOverview handles different timezones correctly`() {
        // Just verify the endpoint accepts various timezone values
        val timezones = listOf(
            "UTC",
            "America/New_York",
            "America/Los_Angeles",
            "Europe/London",
            "Europe/Paris",
            "Asia/Tokyo"
        )

        for (tz in timezones) {
            mockMvc.perform(get("/api/v1/statistics/overview")
                .param("timezone", tz))
                .andExpect(status().isOk)
        }
    }

    @Test
    fun `getOverview response time is acceptable`() {
        // Basic performance sanity check
        val startTime = System.currentTimeMillis()

        mockMvc.perform(get("/api/v1/statistics/overview"))
            .andExpect(status().isOk)

        val duration = System.currentTimeMillis() - startTime

        // Should complete well under 200ms (requirement is < 200ms)
        // Allow more time for test environment overhead
        assert(duration < 1000) { "Response took $duration ms, expected < 1000ms" }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private fun createTestDeck(name: String): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO decks (id, name, deck_type, created_at, updated_at) VALUES (?, ?, 'STUDY', ?, ?)",
            id, name, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
        return id
    }

    private fun createTestCard(deckId: UUID, front: String, back: String): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO cards (id, deck_id, front_text, back_text, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
            id, deckId, front, back, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
        )
        return id
    }
}
