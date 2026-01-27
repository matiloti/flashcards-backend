package com.flashcards.statistics

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.DateTimeException
import java.time.ZoneId

/**
 * REST controller for statistics API.
 */
@RestController
@RequestMapping("/api/v1/statistics")
class StatisticsController(
    private val statisticsService: StatisticsService
) {

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

        val stats = statisticsService.getOverview(zoneId)
        return ResponseEntity.ok(stats)
    }
}
