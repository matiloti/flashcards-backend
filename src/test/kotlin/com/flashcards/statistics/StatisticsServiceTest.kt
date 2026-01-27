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
class StatisticsServiceTest {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var statisticsService: StatisticsService

    private val sentinelUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val utcZone = ZoneId.of("UTC")

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
    // getOverview tests
    // =========================================================================

    @Test
    fun `getOverview returns empty state response for new user`() {
        val response = statisticsService.getOverview(utcZone)

        assertThat(response.streak.current).isEqualTo(0)
        assertThat(response.streak.longest).isEqualTo(0)
        assertThat(response.streak.lastStudyDate).isNull()

        assertThat(response.today.cardsStudied).isEqualTo(0)
        assertThat(response.today.timeMinutes).isEqualTo(0)
        assertThat(response.today.sessionsCompleted).isEqualTo(0)

        assertThat(response.week.days).hasSize(7)
        assertThat(response.week.totalCardsStudied).isEqualTo(0)
        assertThat(response.week.daysStudied).isEqualTo(0)

        assertThat(response.allTime.totalCardsStudied).isEqualTo(0)
        assertThat(response.allTime.totalTimeMinutes).isEqualTo(0)
        assertThat(response.allTime.totalDecks).isEqualTo(0)
        assertThat(response.allTime.totalSessions).isEqualTo(0)

        assertThat(response.cardProgress.mastered).isEqualTo(0)
        assertThat(response.cardProgress.learning).isEqualTo(0)
        assertThat(response.cardProgress.new).isEqualTo(0)
        assertThat(response.cardProgress.total).isEqualTo(0)

        assertThat(response.accuracy.rate).isNull()
        assertThat(response.accuracy.trend).isNull()
        assertThat(response.accuracy.periodDays).isEqualTo(7)

        assertThat(response.topDecks).isEmpty()
    }

    @Test
    fun `getOverview returns correct today stats`() {
        val today = LocalDate.now()

        // Add daily stats for today
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, 20, 15, 2, 10, 5, 5)""",
            java.sql.Date.valueOf(today)
        )

        val response = statisticsService.getOverview(utcZone)

        assertThat(response.today.cardsStudied).isEqualTo(20)
        assertThat(response.today.timeMinutes).isEqualTo(15)
        assertThat(response.today.sessionsCompleted).isEqualTo(2)
    }

    @Test
    fun `getOverview returns correct week stats with 7 days`() {
        val today = LocalDate.now()
        val threeDaysAgo = today.minusDays(3)
        val fiveDaysAgo = today.minusDays(5)

        // Add some study days
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, 20, 15, 2, 10, 5, 5)""",
            java.sql.Date.valueOf(today)
        )
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, 30, 20, 3, 15, 10, 5)""",
            java.sql.Date.valueOf(threeDaysAgo)
        )
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, 25, 18, 2, 12, 8, 5)""",
            java.sql.Date.valueOf(fiveDaysAgo)
        )

        val response = statisticsService.getOverview(utcZone)

        assertThat(response.week.days).hasSize(7)
        assertThat(response.week.totalCardsStudied).isEqualTo(75) // 20 + 30 + 25
        assertThat(response.week.daysStudied).isEqualTo(3)

        // Check day order - should be ascending by date
        val dates = response.week.days.map { it.date }
        assertThat(dates).isSorted

        // Check first and last day
        assertThat(response.week.days.last().date).isEqualTo(today)
        assertThat(response.week.days.first().date).isEqualTo(today.minusDays(6))
    }

    @Test
    fun `getOverview returns all-time stats from user_statistics`() {
        // Set up user statistics
        jdbcTemplate.update(
            """UPDATE user_statistics SET total_cards_studied = 500,
               total_study_time_minutes = 300, total_sessions = 50 WHERE id = ?""",
            sentinelUserId
        )

        // Create some decks
        createTestDeck("Deck 1")
        createTestDeck("Deck 2")
        createTestDeck("Deck 3")

        val response = statisticsService.getOverview(utcZone)

        assertThat(response.allTime.totalCardsStudied).isEqualTo(500)
        assertThat(response.allTime.totalTimeMinutes).isEqualTo(300)
        assertThat(response.allTime.totalDecks).isEqualTo(3)
        assertThat(response.allTime.totalSessions).isEqualTo(50)
    }

    // =========================================================================
    // Streak calculation tests
    // =========================================================================

    @Test
    fun `calculateStreak returns 0 when no study history`() {
        val streak = statisticsService.calculateStreak(utcZone)

        assertThat(streak.current).isEqualTo(0)
        assertThat(streak.longest).isEqualTo(0)
    }

    @Test
    fun `calculateStreak returns 1 when studied today only`() {
        val today = LocalDate.now()
        insertDailyStats(today)

        val streak = statisticsService.calculateStreak(utcZone)

        assertThat(streak.current).isEqualTo(1)
    }

    @Test
    fun `calculateStreak returns consecutive days count`() {
        val today = LocalDate.now()
        // Insert 5 consecutive days
        (0L..4L).forEach { i ->
            insertDailyStats(today.minusDays(i))
        }

        val streak = statisticsService.calculateStreak(utcZone)

        assertThat(streak.current).isEqualTo(5)
    }

    @Test
    fun `calculateStreak breaks on missing day`() {
        val today = LocalDate.now()
        // Today, yesterday, then skip 2 days ago, then 3 days ago
        insertDailyStats(today)
        insertDailyStats(today.minusDays(1))
        // Skip 2 days ago
        insertDailyStats(today.minusDays(3))
        insertDailyStats(today.minusDays(4))

        val streak = statisticsService.calculateStreak(utcZone)

        assertThat(streak.current).isEqualTo(2) // Only today and yesterday
    }

    @Test
    fun `calculateStreak returns 0 if did not study today`() {
        val today = LocalDate.now()
        // Studied yesterday but not today
        insertDailyStats(today.minusDays(1))
        insertDailyStats(today.minusDays(2))
        insertDailyStats(today.minusDays(3))

        val streak = statisticsService.calculateStreak(utcZone)

        assertThat(streak.current).isEqualTo(0) // Streak broken
    }

    @Test
    fun `calculateStreak tracks longest streak separately`() {
        val today = LocalDate.now()
        // Current streak of 2 days
        insertDailyStats(today)
        insertDailyStats(today.minusDays(1))

        // Gap

        // Previous streak of 5 days (should be longest)
        (10L..14L).forEach { i ->
            insertDailyStats(today.minusDays(i))
        }

        val streak = statisticsService.calculateStreak(utcZone)

        assertThat(streak.current).isEqualTo(2)
        assertThat(streak.longest).isGreaterThanOrEqualTo(5) // May be higher if previously stored
    }

    // =========================================================================
    // Accuracy trend tests
    // =========================================================================

    @Test
    fun `calculateAccuracyTrend returns IMPROVING when accuracy increases`() {
        val deckId = createTestDeck("Accuracy Deck")
        val cardId = createTestCard(deckId, "Q1", "A1")
        val sessionId = createTestSession(deckId)

        val today = LocalDate.now()

        // Current period (last 7 days): 8/10 = 80% accuracy
        repeat(8) { createTestReviewOnDate(sessionId, cardId, "EASY", today.minusDays(3)) }
        repeat(2) { createTestReviewOnDate(sessionId, cardId, "AGAIN", today.minusDays(3)) }

        // Previous period (7-14 days ago): 5/10 = 50% accuracy
        repeat(5) { createTestReviewOnDate(sessionId, cardId, "EASY", today.minusDays(10)) }
        repeat(5) { createTestReviewOnDate(sessionId, cardId, "AGAIN", today.minusDays(10)) }

        val accuracy = statisticsService.calculateAccuracyWithTrend(7, utcZone)

        assertThat(accuracy.trend).isEqualTo(AccuracyTrend.IMPROVING)
    }

    @Test
    fun `calculateAccuracyTrend returns DECLINING when accuracy decreases`() {
        val deckId = createTestDeck("Accuracy Deck")
        val cardId = createTestCard(deckId, "Q1", "A1")
        val sessionId = createTestSession(deckId)

        val today = LocalDate.now()

        // Current period: 5/10 = 50% accuracy
        repeat(5) { createTestReviewOnDate(sessionId, cardId, "EASY", today.minusDays(3)) }
        repeat(5) { createTestReviewOnDate(sessionId, cardId, "AGAIN", today.minusDays(3)) }

        // Previous period: 9/10 = 90% accuracy
        repeat(9) { createTestReviewOnDate(sessionId, cardId, "EASY", today.minusDays(10)) }
        repeat(1) { createTestReviewOnDate(sessionId, cardId, "AGAIN", today.minusDays(10)) }

        val accuracy = statisticsService.calculateAccuracyWithTrend(7, utcZone)

        assertThat(accuracy.trend).isEqualTo(AccuracyTrend.DECLINING)
    }

    @Test
    fun `calculateAccuracyTrend returns STABLE when accuracy is similar`() {
        val deckId = createTestDeck("Accuracy Deck")
        val cardId = createTestCard(deckId, "Q1", "A1")
        val sessionId = createTestSession(deckId)

        val today = LocalDate.now()

        // Current period: 7/10 = 70% accuracy
        repeat(7) { createTestReviewOnDate(sessionId, cardId, "EASY", today.minusDays(3)) }
        repeat(3) { createTestReviewOnDate(sessionId, cardId, "AGAIN", today.minusDays(3)) }

        // Previous period: 7/10 = 70% accuracy (same)
        repeat(7) { createTestReviewOnDate(sessionId, cardId, "EASY", today.minusDays(10)) }
        repeat(3) { createTestReviewOnDate(sessionId, cardId, "AGAIN", today.minusDays(10)) }

        val accuracy = statisticsService.calculateAccuracyWithTrend(7, utcZone)

        assertThat(accuracy.trend).isEqualTo(AccuracyTrend.STABLE)
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private fun insertDailyStats(date: LocalDate) {
        jdbcTemplate.update(
            """INSERT INTO daily_study_stats (study_date, cards_studied, time_minutes,
               sessions_completed, easy_count, hard_count, again_count)
               VALUES (?, 10, 5, 1, 5, 3, 2)
               ON CONFLICT (study_date) DO NOTHING""",
            java.sql.Date.valueOf(date)
        )
    }

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

    private fun createTestReviewOnDate(sessionId: UUID, cardId: UUID, rating: String, date: LocalDate) {
        val id = UUID.randomUUID()
        // Set reviewed_at to noon on the given date
        val reviewedAt = date.atStartOfDay(utcZone).plusHours(12).toInstant()
        jdbcTemplate.update(
            "INSERT INTO card_reviews (id, session_id, card_id, rating, reviewed_at) VALUES (?, ?, ?, ?, ?)",
            id, sessionId, cardId, rating, java.sql.Timestamp.from(reviewedAt)
        )
    }
}
