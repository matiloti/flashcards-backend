package com.flashcards.tag

import java.util.UUID

/**
 * Exception thrown when a tag is not found.
 */
class TagNotFoundException(val tagId: UUID) : RuntimeException("Tag not found: $tagId")

/**
 * Exception thrown when attempting to create/update a tag with a duplicate name.
 */
class DuplicateTagNameException(val name: String) : RuntimeException("A tag named '$name' already exists")

/**
 * Exception thrown when attempting to assign more than 5 tags to a deck.
 */
class TooManyTagsException(val provided: Int, val max: Int = 5) :
    RuntimeException("Maximum $max tags allowed per deck, but $provided were provided")

/**
 * Exception thrown when one or more tag IDs are invalid (not found or don't belong to user).
 */
class InvalidTagIdsException(val invalidIds: List<UUID>) :
    RuntimeException("Invalid tag IDs: $invalidIds")
