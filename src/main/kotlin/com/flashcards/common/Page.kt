package com.flashcards.common

/**
 * Generic paginated response wrapper.
 * Follows Spring Data Page structure for frontend compatibility.
 *
 * @param T The type of elements in this page
 */
data class Page<T>(
    /** The content of this page */
    val content: List<T>,
    /** Total number of elements across all pages */
    val totalElements: Long,
    /** Total number of pages */
    val totalPages: Int,
    /** Current page number (0-indexed) */
    val number: Int,
    /** Page size (number of elements per page) */
    val size: Int,
    /** True if this is the first page */
    val first: Boolean,
    /** True if this is the last page */
    val last: Boolean
)
