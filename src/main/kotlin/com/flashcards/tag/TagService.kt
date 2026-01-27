package com.flashcards.tag

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class TagService(
    private val tagRepository: TagRepository,
    private val deckTagRepository: DeckTagRepository
) {

    companion object {
        const val MAX_TAG_NAME_LENGTH = 30
        const val MAX_TAGS_PER_DECK = 5
    }

    /**
     * Get all tags for a user with deck counts.
     */
    fun getAllTags(userId: UUID): List<TagResponse> {
        return tagRepository.findByUserIdWithDeckCount(userId).map { it.toResponse() }
    }

    /**
     * Get paginated tags for a user with deck counts.
     * @param userId The user ID
     * @param page Page number (0-indexed)
     * @param size Page size (max 100)
     * @param sort Sort string in format "field,direction" (e.g., "name,asc")
     */
    fun getTagsPaginated(userId: UUID, page: Int, size: Int, sort: String): TagListResponse {
        val (sortField, sortDir) = parseSortParam(sort)
        val tags = tagRepository.findByUserIdWithDeckCountPaginated(userId, page, size, sortField, sortDir)
        val totalElements = tagRepository.countByUserId(userId)

        return TagListResponse(
            content = tags.map { it.toResponse() },
            totalElements = totalElements,
            page = page,
            size = size
        )
    }

    private fun parseSortParam(sort: String): Pair<String, String> {
        val parts = sort.split(",")
        val field = parts.getOrElse(0) { "name" }.trim()
        val direction = parts.getOrElse(1) { "asc" }.trim()
        return Pair(field, direction)
    }

    /**
     * Get a single tag by ID with deck count.
     * @throws TagNotFoundException if tag not found or belongs to different user
     */
    fun getTagById(userId: UUID, tagId: UUID): TagResponse {
        val tag = tagRepository.findByIdAndUserId(tagId, userId)
            ?: throw TagNotFoundException(tagId)

        val deckCount = deckTagRepository.countByTagId(tagId)
        return TagResponse(
            id = tag.id,
            name = tag.name,
            deckCount = deckCount,
            createdAt = tag.createdAt
        )
    }

    /**
     * Create a new tag.
     * @throws DuplicateTagNameException if tag with same name exists (case-insensitive)
     * @throws IllegalArgumentException if name is blank or exceeds 30 chars
     */
    fun createTag(userId: UUID, request: CreateTagRequest): TagResponse {
        val trimmedName = request.name.trim()

        validateTagName(trimmedName)

        if (tagRepository.existsByUserIdAndNameIgnoreCase(userId, trimmedName)) {
            throw DuplicateTagNameException(trimmedName)
        }

        val tag = Tag(
            id = UUID.randomUUID(),
            name = trimmedName,
            userId = userId,
            createdAt = Instant.now()
        )

        val saved = tagRepository.save(tag)
        return TagResponse(
            id = saved.id,
            name = saved.name,
            deckCount = 0,
            createdAt = saved.createdAt
        )
    }

    /**
     * Update a tag's name.
     * @throws TagNotFoundException if tag not found or belongs to different user
     * @throws DuplicateTagNameException if another tag with same name exists
     * @throws IllegalArgumentException if name is blank or exceeds 30 chars
     */
    fun updateTag(userId: UUID, tagId: UUID, request: UpdateTagRequest): TagResponse {
        val trimmedName = request.name.trim()

        validateTagName(trimmedName)

        val tag = tagRepository.findByIdAndUserId(tagId, userId)
            ?: throw TagNotFoundException(tagId)

        // Check for duplicate name (excluding current tag)
        if (tagRepository.existsByUserIdAndNameIgnoreCaseExcludingId(userId, trimmedName, tagId)) {
            throw DuplicateTagNameException(trimmedName)
        }

        val updated = tagRepository.update(tag.copy(name = trimmedName))
        val deckCount = deckTagRepository.countByTagId(tagId)

        return TagResponse(
            id = updated.id,
            name = updated.name,
            deckCount = deckCount,
            createdAt = updated.createdAt
        )
    }

    /**
     * Delete a tag.
     * @throws TagNotFoundException if tag not found or belongs to different user
     */
    fun deleteTag(userId: UUID, tagId: UUID) {
        val tag = tagRepository.findByIdAndUserId(tagId, userId)
            ?: throw TagNotFoundException(tagId)

        tagRepository.deleteById(tag.id)
    }

    /**
     * Validate tag IDs belong to user and return valid tags.
     * @throws InvalidTagIdsException if any tag IDs are invalid
     */
    fun validateTagIds(userId: UUID, tagIds: List<UUID>): List<Tag> {
        if (tagIds.isEmpty()) return emptyList()

        val uniqueTagIds = tagIds.distinct()

        if (uniqueTagIds.size > MAX_TAGS_PER_DECK) {
            throw TooManyTagsException(uniqueTagIds.size, MAX_TAGS_PER_DECK)
        }

        val foundTags = tagRepository.findByIdsAndUserId(uniqueTagIds, userId)
        val foundIds = foundTags.map { it.id }.toSet()
        val invalidIds = uniqueTagIds.filter { it !in foundIds }

        if (invalidIds.isNotEmpty()) {
            throw InvalidTagIdsException(invalidIds)
        }

        return foundTags
    }

    private fun validateTagName(name: String) {
        if (name.isBlank()) {
            throw IllegalArgumentException("Tag name must not be blank")
        }
        if (name.length > MAX_TAG_NAME_LENGTH) {
            throw IllegalArgumentException("Tag name must not exceed $MAX_TAG_NAME_LENGTH characters")
        }
    }

    private fun TagWithDeckCount.toResponse() = TagResponse(
        id = this.id,
        name = this.name,
        deckCount = this.deckCount,
        createdAt = this.createdAt
    )
}
