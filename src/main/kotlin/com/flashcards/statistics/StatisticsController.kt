package com.flashcards.statistics

import com.flashcards.security.JwtAuthentication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DateTimeException
import java.time.ZoneId
import java.util.UUID

/**
 * REST controller for statistics API.
 */
@RestController
@RequestMapping("/api/v1/statistics")
class StatisticsController(
    private val statisticsService: StatisticsService
) {

    /**
     * Get the current authenticated user's ID from the SecurityContext.
     */
    private fun getCurrentUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication as JwtAuthentication
        return authentication.userId
    }

    /**
     * GET /api/v1/statistics/overview
     *
     * Returns a comprehensive statistics overview for the user.
     * Accepts an optional timezone parameter for accurate streak and "today" calculations.
     *
     * @param timezone IANA timezone identifier (e.g., "America/New_York"). Defaults to UTC.
     * @return StatisticsOverviewResponse with all statistics data
     */
    @GetMapping("/overview")
    fun getOverview(
        @RequestParam(defaultValue = "UTC") timezone: String
    ): ResponseEntity<Any> {
        val zoneId = try {
            ZoneId.of(timezone)
        } catch (e: DateTimeException) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(InvalidTimezoneError(
                    error = "Invalid timezone",
                    validExample = "America/New_York"
                ))
        }

        val userId = getCurrentUserId()
        val stats = statisticsService.getOverview(userId, zoneId)
        return ResponseEntity.ok(stats)
    }
}
