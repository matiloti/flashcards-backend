package com.flashcards.statistics

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class StatisticsRepositoryTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var statisticsRepository: StatisticsRepository

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
            """INSERT INTO user_statistics (id, user_id, current_streak, longest_streak, total_cards_studied,
               total_study_time_minutes, total_sessions) VALUES (?, ?, 0, 0, 0, 0, 0)
               ON CONFLICT (user_id) DO NOTHING""",
            sentinelUserId, sentinelUserId
        )
    }

    // =========================================================================
    // getUserStats tests
    // =========================================================================

    @Test
    fun `getUserStats returns empty stats for new user`() {
        val stats = statisticsRepository.getUserStats(sentinelUserId)

        assertThat(stats).isNotNull
        assertThat(stats.currentStreak).isEqualTo(0)
        assertThat(stats.longestStreak).isEqualTo(0)
        assertThat(stats.lastStudyDate).isNull()
        assertThat(stats.totalCardsStudied).isEqualTo(0)
        assertThat(stats.totalStudyTimeMinutes).isEqualTo(0)
        assertThat(stats.totalSessions).isEqualTo(0)
    }

    @Test
    fun `getUserStats returns populated stats`() {
        val today = LocalDate.now()
        jdbcTemplate.update(
            """UPDATE user_statistics SET current_streak = 5, longest_streak = 10,
               last_study_date = ?, total_cards_studied = 100, total_study_time_minutes = 60,
               total_sessions = 15 WHERE user_id = ?""",
            java.sql.Date.valueOf(today), sentinelUserId
        )

        val stats = statisticsRepository.getUserStats(sentinelUserId)

        assertThat(stats.currentStreak).isEqualTo(5)
        assertThat(stats.longestStreak).isEqualTo(10)
        assertThat(stats.lastStudyDate).isEqualTo(today)
        assertThat(stats.totalCardsStudied).isEqualTo(100)
        assertThat(stats.totalStudyTimeMinutes).isEqualTo(60)
        assertThat(stats.totalSessions).isEqualTo(15)
    }

    // =========================================================================
    // getDailyStats tests
    // =========================================================================

    @Test
    fun `getDailyStats returns empty list when no data`() {
        val zoneId = ZoneId.of("UTC")
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(6)

        val stats = statisticsRepository.getDailyStats(sentinelUserId, startDate, endDate, zoneId)

        assertThat(stats).isEmpty()
    }

    @Test
    fun `getDailyStats returns daily stats for date range`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Insert some daily stats
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (user_id, study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, ?, 20, 15, 2, 10, 5, 5)""",
            sentinelUserId, java.sql.Date.valueOf(today)
        )
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (user_id, study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, ?, 30, 25, 3, 15, 10, 5)""",
            sentinelUserId, java.sql.Date.valueOf(yesterday)
        )

        val stats = statisticsRepository.getDailyStats(sentinelUserId, yesterday, today, ZoneId.of("UTC"))

        assertThat(stats).hasSize(2)
        val todayStats = stats.find { it.studyDate == today }
        assertThat(todayStats).isNotNull
        assertThat(todayStats!!.cardsStudied).isEqualTo(20)
        assertThat(todayStats.timeMinutes).isEqualTo(15)
        assertThat(todayStats.sessionsCompleted).isEqualTo(2)
    }

    // =========================================================================
    // getCardProgressCounts tests
    // =========================================================================

    @Test
    fun `getCardProgressCounts returns zeros when no cards`() {
        val counts = statisticsRepository.getCardProgressCounts(sentinelUserId)

        assertThat(counts.mastered).isEqualTo(0)
        assertThat(counts.learning).isEqualTo(0)
        assertThat(counts.new).isEqualTo(0)
        assertThat(counts.total).isEqualTo(0)
    }

    @Test
    fun `getCardProgressCounts counts cards by mastery level`() {
        // Create a deck first
        val deckId = createTestDeck("Test Deck")

        // Create cards
        val card1 = createTestCard(deckId, "Q1", "A1")
        val card2 = createTestCard(deckId, "Q2", "A2")
        val card3 = createTestCard(deckId, "Q3", "A3")
        val card4 = createTestCard(deckId, "Q4", "A4")

        // Create card progress entries
        jdbcTemplate.update(
            """INSERT INTO card_progress (card_id, mastery_level, consecutive_easy_count, total_reviews)
               VALUES (?, 'MASTERED', 3, 5)""", card1
        )
        jdbcTemplate.update(
            """INSERT INTO card_progress (card_id, mastery_level, consecutive_easy_count, total_reviews)
               VALUES (?, 'LEARNING', 1, 2)""", card2
        )
        // card3 and card4 have no card_progress entries (NEW cards)

        val counts = statisticsRepository.getCardProgressCounts(sentinelUserId)

        assertThat(counts.mastered).isEqualTo(1)
        assertThat(counts.learning).isEqualTo(1)
        assertThat(counts.new).isEqualTo(2) // cards without card_progress
        assertThat(counts.total).isEqualTo(4)
    }

    // =========================================================================
    // getAccuracyStats tests
    // =========================================================================

    @Test
    fun `getAccuracyStats returns null rate when no reviews`() {
        val stats = statisticsRepository.getAccuracyStats(sentinelUserId, 7, ZoneId.of("UTC"))

        assertThat(stats.rate).isNull()
        assertThat(stats.trend).isNull()
        assertThat(stats.easyCount).isEqualTo(0)
        assertThat(stats.hardCount).isEqualTo(0)
        assertThat(stats.againCount).isEqualTo(0)
    }

    @Test
    fun `getAccuracyStats calculates accuracy from reviews`() {
        val deckId = createTestDeck("Accuracy Test Deck")
        val cardId = createTestCard(deckId, "Q1", "A1")
        val sessionId = createTestSession(deckId)

        // Create reviews: 6 EASY, 3 HARD, 1 AGAIN = 90% accuracy
        repeat(6) { createTestReview(sessionId, cardId, "EASY") }
        repeat(3) { createTestReview(sessionId, cardId, "HARD") }
        repeat(1) { createTestReview(sessionId, cardId, "AGAIN") }

        val stats = statisticsRepository.getAccuracyStats(sentinelUserId, 7, ZoneId.of("UTC"))

        assertThat(stats.easyCount).isEqualTo(6)
        assertThat(stats.hardCount).isEqualTo(3)
        assertThat(stats.againCount).isEqualTo(1)
        assertThat(stats.rate).isEqualTo(0.9) // (6+3)/(6+3+1) = 0.9
    }

    // =========================================================================
    // getTopDecks tests
    // =========================================================================

    @Test
    fun `getTopDecks returns empty list when no studied decks`() {
        val topDecks = statisticsRepository.getTopDecks(sentinelUserId, 5)

        assertThat(topDecks).isEmpty()
    }

    @Test
    fun `getTopDecks returns decks ordered by last studied`() {
        // Create two decks
        val deck1Id = createTestDeck("Deck 1")
        val deck2Id = createTestDeck("Deck 2")

        // Add cards to both
        val card1 = createTestCard(deck1Id, "Q1", "A1")
        val card2 = createTestCard(deck2Id, "Q2", "A2")

        // Create sessions with different timestamps
        val session1Id = createTestSession(deck1Id)
        val session2Id = createTestSession(deck2Id)

        // Complete session 1 first, then session 2 (deck 2 is more recent)
        val oldTime = Instant.now().minus(1, ChronoUnit.HOURS)
        val newTime = Instant.now()

        jdbcTemplate.update(
            "UPDATE study_sessions SET completed_at = ? WHERE id = ?",
            java.sql.Timestamp.from(oldTime), session1Id
        )
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?",
            java.sql.Timestamp.from(oldTime), deck1Id
        )
        jdbcTemplate.update(
            "UPDATE study_sessions SET completed_at = ? WHERE id = ?",
            java.sql.Timestamp.from(newTime), session2Id
        )
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?",
            java.sql.Timestamp.from(newTime), deck2Id
        )

        val topDecks = statisticsRepository.getTopDecks(sentinelUserId, 5)

        assertThat(topDecks).hasSize(2)
        assertThat(topDecks[0].name).isEqualTo("Deck 2") // Most recently studied first
        assertThat(topDecks[1].name).isEqualTo("Deck 1")
    }

    @Test
    fun `getTopDecks calculates progress percentage correctly`() {
        val deckId = createTestDeck("Progress Deck")

        // Create 4 cards
        val card1 = createTestCard(deckId, "Q1", "A1")
        val card2 = createTestCard(deckId, "Q2", "A2")
        val card3 = createTestCard(deckId, "Q3", "A3")
        val card4 = createTestCard(deckId, "Q4", "A4")

        // Make 2 cards mastered
        jdbcTemplate.update(
            """INSERT INTO card_progress (card_id, mastery_level, consecutive_easy_count, total_reviews)
               VALUES (?, 'MASTERED', 3, 5)""", card1
        )
        jdbcTemplate.update(
            """INSERT INTO card_progress (card_id, mastery_level, consecutive_easy_count, total_reviews)
               VALUES (?, 'MASTERED', 4, 6)""", card2
        )

        // Mark deck as studied
        val sessionId = createTestSession(deckId)
        val now = Instant.now()
        jdbcTemplate.update(
            "UPDATE study_sessions SET completed_at = ? WHERE id = ?",
            java.sql.Timestamp.from(now), sessionId
        )
        jdbcTemplate.update(
            "UPDATE decks SET last_studied_at = ? WHERE id = ?",
            java.sql.Timestamp.from(now), deckId
        )

        val topDecks = statisticsRepository.getTopDecks(sentinelUserId, 5)

        assertThat(topDecks).hasSize(1)
        assertThat(topDecks[0].masteredCards).isEqualTo(2)
        assertThat(topDecks[0].totalCards).isEqualTo(4)
        assertThat(topDecks[0].progressPercent).isEqualTo(50) // 2/4 = 50%
    }

    // =========================================================================
    // getDeckCount tests
    // =========================================================================

    @Test
    fun `getDeckCount returns zero when no decks`() {
        val count = statisticsRepository.getDeckCount(sentinelUserId)
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `getDeckCount returns total deck count`() {
        createTestDeck("Deck 1")
        createTestDeck("Deck 2")
        createTestDeck("Deck 3")

        val count = statisticsRepository.getDeckCount(sentinelUserId)
        assertThat(count).isEqualTo(3)
    }

    // =========================================================================
    // updateUserStats tests
    // =========================================================================

    @Test
    fun `updateUserStats updates cumulative statistics`() {
        statisticsRepository.updateUserStats(
            userId = sentinelUserId,
            cardsStudied = 10,
            studyTimeMinutes = 5,
            sessionsCompleted = 1,
            studyDate = LocalDate.now()
        )

        val stats = statisticsRepository.getUserStats(sentinelUserId)

        assertThat(stats.totalCardsStudied).isEqualTo(10)
        assertThat(stats.totalStudyTimeMinutes).isEqualTo(5)
        assertThat(stats.totalSessions).isEqualTo(1)
    }

    @Test
    fun `updateUserStats increments existing stats`() {
        // Set initial stats
        jdbcTemplate.update(
            """UPDATE user_statistics SET total_cards_studied = 50,
               total_study_time_minutes = 30, total_sessions = 5 WHERE user_id = ?""",
            sentinelUserId
        )

        statisticsRepository.updateUserStats(
            userId = sentinelUserId,
            cardsStudied = 10,
            studyTimeMinutes = 5,
            sessionsCompleted = 1,
            studyDate = LocalDate.now()
        )

        val stats = statisticsRepository.getUserStats(sentinelUserId)

        assertThat(stats.totalCardsStudied).isEqualTo(60)
        assertThat(stats.totalStudyTimeMinutes).isEqualTo(35)
        assertThat(stats.totalSessions).isEqualTo(6)
    }

    // =========================================================================
    // upsertDailyStats tests
    // =========================================================================

    @Test
    fun `upsertDailyStats creates new daily stats`() {
        val today = LocalDate.now()

        statisticsRepository.upsertDailyStats(
            userId = sentinelUserId,
            studyDate = today,
            cardsStudied = 20,
            timeMinutes = 15,
            easyCount = 10,
            hardCount = 5,
            againCount = 5
        )

        val stats = statisticsRepository.getDailyStats(sentinelUserId, today, today, ZoneId.of("UTC"))

        assertThat(stats).hasSize(1)
        assertThat(stats[0].cardsStudied).isEqualTo(20)
        assertThat(stats[0].timeMinutes).isEqualTo(15)
        assertThat(stats[0].sessionsCompleted).isEqualTo(1)
    }

    @Test
    fun `upsertDailyStats updates existing daily stats`() {
        val today = LocalDate.now()

        // First session
        statisticsRepository.upsertDailyStats(sentinelUserId, today, 20, 15, 10, 5, 5)

        // Second session same day
        statisticsRepository.upsertDailyStats(sentinelUserId, today, 30, 20, 15, 10, 5)

        val stats = statisticsRepository.getDailyStats(sentinelUserId, today, today, ZoneId.of("UTC"))

        assertThat(stats).hasSize(1)
        assertThat(stats[0].cardsStudied).isEqualTo(50) // 20 + 30
        assertThat(stats[0].timeMinutes).isEqualTo(35) // 15 + 20
        assertThat(stats[0].sessionsCompleted).isEqualTo(2)
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private fun createTestDeck(name: String): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO decks (id, name, deck_type, user_id, created_at, updated_at) VALUES (?, ?, 'STUDY', ?, ?, ?)",
            id, name, sentinelUserId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)
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

    private fun createTestSession(deckId: UUID): UUID {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            """INSERT INTO study_sessions (id, deck_id, session_type, started_at, cards_studied,
               cards_easy, cards_hard, cards_again) VALUES (?, ?, 'STUDY', ?, 0, 0, 0, 0)""",
            id, deckId, java.sql.Timestamp.from(now)
        )
        return id
    }

    private fun createTestReview(sessionId: UUID, cardId: UUID, rating: String) {
        val id = UUID.randomUUID()
        val now = Instant.now()
        jdbcTemplate.update(
            "INSERT INTO card_reviews (id, session_id, card_id, rating, reviewed_at) VALUES (?, ?, ?, ?, ?)",
            id, sessionId, cardId, rating, java.sql.Timestamp.from(now)
        )
    }
}
