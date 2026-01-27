package com.flashcards.tag

import java.util.UUID

/**
 * Request DTO for creating a new tag.
 */
data class CreateTagRequest(
    val name: String
)

/**
 * Request DTO for updating a tag's name.
 */
data class UpdateTagRequest(
    val name: String
)

/**
 * Request DTO for updating tags assigned to a deck.
 */
data class UpdateDeckTagsRequest(
    val tagIds: List<UUID>
)
