package com.flashcards.statistics

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Service for calculating and aggregating statistics data.
 */
@Service
class StatisticsService(
    private val statisticsRepository: StatisticsRepository
) {

    companion object {
        const val ACCURACY_PERIOD_DAYS = 7
        const val TREND_THRESHOLD = 0.05 // 5% difference for improving/declining
    }

    /**
     * Get complete statistics overview for the user.
     * This is the main entry point for the statistics API.
     */
    fun getOverview(userId: UUID, zoneId: ZoneId): StatisticsOverviewResponse {
        val today = LocalDate.now(zoneId)
        val weekStart = today.minusDays(6)

        // Get user stats (streak, all-time totals)
        val userStats = statisticsRepository.getUserStats(userId)

        // Get daily stats for the week
        val dailyStats = statisticsRepository.getDailyStats(userId, weekStart, today, zoneId)
        val dailyStatsMap = dailyStats.associateBy { it.studyDate }

        // Build week days
        val weekDays = (0L..6L).map { daysAgo ->
            val date = weekStart.plusDays(daysAgo)
            val stats = dailyStatsMap[date]
            DayStats(
                date = date,
                cardsStudied = stats?.cardsStudied ?: 0,
                studied = stats != null
            )
        }

        // Get today's stats
        val todayStats = dailyStatsMap[today]
        val todayResponse = TodayStats(
            cardsStudied = todayStats?.cardsStudied ?: 0,
            timeMinutes = todayStats?.timeMinutes ?: 0,
            sessionsCompleted = todayStats?.sessionsCompleted ?: 0
        )

        // Week summary
        val weekResponse = WeekStats(
            days = weekDays,
            totalCardsStudied = dailyStats.sumOf { it.cardsStudied },
            daysStudied = dailyStats.size
        )

        // All-time stats
        val deckCount = statisticsRepository.getDeckCount(userId)
        val allTimeResponse = AllTimeStats(
            totalCardsStudied = userStats.totalCardsStudied,
            totalTimeMinutes = userStats.totalStudyTimeMinutes,
            totalDecks = deckCount,
            totalSessions = userStats.totalSessions
        )

        // Card progress
        val cardProgress = statisticsRepository.getCardProgressCounts(userId)

        // Accuracy with trend
        val accuracy = calculateAccuracyWithTrend(userId, ACCURACY_PERIOD_DAYS, zoneId)

        // Top decks
        val topDecks = statisticsRepository.getTopDecks(userId, 5)

        // Streak (calculate fresh, considering timezone)
        val streak = calculateStreak(userId, zoneId)

        return StatisticsOverviewResponse(
            streak = streak,
            today = todayResponse,
            week = weekResponse,
            allTime = allTimeResponse,
            cardProgress = cardProgress,
            accuracy = accuracy,
            topDecks = topDecks
        )
    }

    /**
     * Calculate the current and longest streak.
     * A streak is consecutive days with at least one study session.
     * If user hasn't studied today, current streak is 0.
     */
    fun calculateStreak(userId: UUID, zoneId: ZoneId): StreakStats {
        val today = LocalDate.now(zoneId)
        val studyDates = statisticsRepository.getStudyDates(userId) // Ordered DESC

        if (studyDates.isEmpty()) {
            return StreakStats(current = 0, longest = 0, lastStudyDate = null)
        }

        val lastStudyDate = studyDates.first()

        // Current streak: must include today, count consecutive days backward
        var currentStreak = 0
        if (studyDates.contains(today)) {
            currentStreak = 1
            var expectedDate = today.minusDays(1)
            for (date in studyDates.drop(1)) {
                if (date == expectedDate) {
                    currentStreak++
                    expectedDate = expectedDate.minusDays(1)
                } else if (date < expectedDate) {
                    // Gap found, streak breaks
                    break
                }
                // If date > expectedDate (duplicate or future date), skip
            }
        }

        // Calculate longest streak from all study dates
        val longestStreak = calculateLongestStreak(studyDates)

        // Get stored longest streak (may be higher than calculated if older data purged)
        val userStats = statisticsRepository.getUserStats(userId)
        val effectiveLongestStreak = maxOf(longestStreak, userStats.longestStreak, currentStreak)

        return StreakStats(
            current = currentStreak,
            longest = effectiveLongestStreak,
            lastStudyDate = lastStudyDate
        )
    }

    /**
     * Calculate the longest consecutive streak from a list of study dates.
     */
    private fun calculateLongestStreak(studyDates: List<LocalDate>): Int {
        if (studyDates.isEmpty()) return 0

        val sortedDates = studyDates.distinct().sortedDescending()
        var longest = 1
        var current = 1

        for (i in 1 until sortedDates.size) {
            val prev = sortedDates[i - 1]
            val curr = sortedDates[i]
            if (prev.minusDays(1) == curr) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }

        return longest
    }

    /**
     * Calculate accuracy for the specified period and determine trend.
     */
    fun calculateAccuracyWithTrend(userId: UUID, periodDays: Int, zoneId: ZoneId): AccuracyStats {
        val today = LocalDate.now(zoneId)

        // Current period: last N days
        val currentEndDate = today
        val currentStartDate = today.minusDays(periodDays.toLong() - 1)

        // Previous period: N to 2N days ago
        val previousEndDate = currentStartDate.minusDays(1)
        val previousStartDate = previousEndDate.minusDays(periodDays.toLong() - 1)

        val currentStats = statisticsRepository.getAccuracyStatsForRange(userId, currentStartDate, currentEndDate)
        val previousStats = statisticsRepository.getAccuracyStatsForRange(userId, previousStartDate, previousEndDate)

        val trend = calculateTrend(currentStats.rate, previousStats.rate)

        return AccuracyStats(
            rate = currentStats.rate,
            trend = trend,
            periodDays = periodDays,
            easyCount = currentStats.easyCount,
            hardCount = currentStats.hardCount,
            againCount = currentStats.againCount
        )
    }

    /**
     * Determine accuracy trend based on rate comparison.
     */
    private fun calculateTrend(currentRate: Double?, previousRate: Double?): AccuracyTrend? {
        if (currentRate == null || previousRate == null) {
            return null
        }

        val difference = currentRate - previousRate

        return when {
            difference > TREND_THRESHOLD -> AccuracyTrend.IMPROVING
            difference < -TREND_THRESHOLD -> AccuracyTrend.DECLINING
            else -> AccuracyTrend.STABLE
        }
    }

    /**
     * Update statistics after a study session is completed.
     * This should be called from StudyController.completeSession.
     */
    fun recordSessionCompletion(
        userId: UUID,
        cardsStudied: Int,
        easyCount: Int,
        hardCount: Int,
        againCount: Int,
        sessionDurationMinutes: Int,
        zoneId: ZoneId
    ) {
        val today = LocalDate.now(zoneId)

        // Ensure user_statistics row exists
        statisticsRepository.ensureUserStatsExists(userId)

        // Update daily stats
        statisticsRepository.upsertDailyStats(
            userId = userId,
            studyDate = today,
            cardsStudied = cardsStudied,
            timeMinutes = sessionDurationMinutes,
            easyCount = easyCount,
            hardCount = hardCount,
            againCount = againCount
        )

        // Update user stats
        statisticsRepository.updateUserStats(
            userId = userId,
            cardsStudied = cardsStudied,
            studyTimeMinutes = sessionDurationMinutes,
            sessionsCompleted = 1,
            studyDate = today
        )

        // Recalculate and update streak
        val streak = calculateStreak(userId, zoneId)
        statisticsRepository.updateStreak(userId, streak.current, streak.longest)
    }

    /**
     * Record a card review and update card progress.
     * This should be called from StudyController.submitReview.
     */
    fun recordCardReview(
        cardId: UUID,
        rating: String,
        reviewedAt: java.time.Instant
    ) {
        statisticsRepository.upsertCardProgress(cardId, rating, reviewedAt)
    }
}
