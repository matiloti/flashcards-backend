package com.flashcards.tag

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/tags")
class TagController(private val tagService: TagService) {

    companion object {
        // Single-user mode sentinel user ID
        private val SENTINEL_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private const val MAX_SIZE = 100
    }

    /**
     * GET /api/v1/tags
     * List all tags for the current user with deck counts (paginated).
     */
    @GetMapping
    fun listTags(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(defaultValue = "name,asc") sort: String
    ): ResponseEntity<Any> {
        // Validate pagination parameters
        if (page < 0) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_PAGINATION",
                    "message" to "Page must be >= 0"
                )
            )
        }
        if (size < 1 || size > MAX_SIZE) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_PAGINATION",
                    "message" to "Size must be between 1 and $MAX_SIZE"
                )
            )
        }

        val result = tagService.getTagsPaginated(SENTINEL_USER_ID, page, size, sort)
        return ResponseEntity.ok(result)
    }

    /**
     * POST /api/v1/tags
     * Create a new tag.
     */
    @PostMapping
    fun createTag(@RequestBody request: CreateTagRequest): ResponseEntity<Any> {
        return try {
            val tag = tagService.createTag(SENTINEL_USER_ID, request)
            ResponseEntity.status(HttpStatus.CREATED).body(tag)
        } catch (e: DuplicateTagNameException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "DUPLICATE_TAG_NAME",
                    "message" to e.message,
                    "field" to "name"
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_TAG_NAME",
                    "message" to e.message,
                    "field" to "name"
                )
            )
        }
    }

    /**
     * GET /api/v1/tags/{tagId}
     * Get a single tag by ID.
     */
    @GetMapping("/{tagId}")
    fun getTag(@PathVariable tagId: UUID): ResponseEntity<Any> {
        return try {
            val tag = tagService.getTagById(SENTINEL_USER_ID, tagId)
            ResponseEntity.ok(tag)
        } catch (e: TagNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf(
                    "error" to "TAG_NOT_FOUND",
                    "message" to "Tag not found: $tagId"
                )
            )
        }
    }

    /**
     * PATCH /api/v1/tags/{tagId}
     * Update a tag's name.
     */
    @PatchMapping("/{tagId}")
    fun updateTag(
        @PathVariable tagId: UUID,
        @RequestBody request: UpdateTagRequest
    ): ResponseEntity<Any> {
        return try {
            val tag = tagService.updateTag(SENTINEL_USER_ID, tagId, request)
            ResponseEntity.ok(tag)
        } catch (e: TagNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf(
                    "error" to "TAG_NOT_FOUND",
                    "message" to "Tag not found: $tagId"
                )
            )
        } catch (e: DuplicateTagNameException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "DUPLICATE_TAG_NAME",
                    "message" to e.message,
                    "field" to "name"
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to "INVALID_TAG_NAME",
                    "message" to e.message,
                    "field" to "name"
                )
            )
        }
    }

    /**
     * DELETE /api/v1/tags/{tagId}
     * Delete a tag.
     */
    @DeleteMapping("/{tagId}")
    fun deleteTag(@PathVariable tagId: UUID): ResponseEntity<Any> {
        return try {
            tagService.deleteTag(SENTINEL_USER_ID, tagId)
            ResponseEntity.noContent().build()
        } catch (e: TagNotFoundException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf(
                    "error" to "TAG_NOT_FOUND",
                    "message" to "Tag not found: $tagId"
                )
            )
        }
    }
}
