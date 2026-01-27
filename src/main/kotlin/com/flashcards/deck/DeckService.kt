package com.flashcards.deck

import com.flashcards.tag.*
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeckService(
    private val deckRepository: DeckRepository,
    private val tagService: TagService,
    private val deckTagRepository: DeckTagRepository
) {

    /**
     * Get all decks with their tags for a user.
     */
    fun getAllDecks(userId: UUID, tagId: UUID? = null, untagged: Boolean? = null): List<DeckResponse> {
        val decks = when {
            tagId != null -> deckRepository.findAllByTagId(userId, tagId)
            untagged == true -> deckRepository.findAllUntagged(userId)
            else -> deckRepository.findAll(userId)
        }

        // Batch load tags to avoid N+1
        val deckIds = decks.map { it.id }
        val tagsByDeck = deckTagRepository.findTagsForDecks(deckIds)

        return decks.map { deck ->
            deck.toResponse(tagsByDeck[deck.id] ?: emptyList())
        }
    }

    /**
     * Get a single deck by ID with tags.
     */
    fun getDeckById(userId: UUID, id: UUID): DeckResponse? {
        val deck = deckRepository.findById(id, userId) ?: return null
        val tags = deckTagRepository.findTagsByDeckId(id)
        return deck.toResponse(tags)
    }

    /**
     * Create a new deck with optional tags.
     */
    fun createDeck(userId: UUID, request: CreateDeckRequest): DeckResponse {
        val name = request.name.trim()
        val type = request.type ?: DeckType.STUDY
        val tagIds = request.tagIds ?: emptyList()

        // Validate tags if provided
        if (tagIds.isNotEmpty()) {
            tagService.validateTagIds(userId, tagIds)
        }

        val deck = deckRepository.create(name, type, userId)

        // Assign tags
        if (tagIds.isNotEmpty()) {
            deckTagRepository.setTagsForDeck(deck.id, tagIds)
        }

        val tags = deckTagRepository.findTagsByDeckId(deck.id)
        return deck.toResponse(tags)
    }

    /**
     * Update deck tags.
     * @throws DeckNotFoundException if deck not found
     * @throws TooManyTagsException if more than 5 tags
     * @throws InvalidTagIdsException if any tag IDs are invalid
     */
    fun updateDeckTags(userId: UUID, deckId: UUID, tagIds: List<UUID>): DeckResponse {
        val deck = deckRepository.findById(deckId, userId)
            ?: throw DeckNotFoundException(deckId)

        // Validate tags
        if (tagIds.isNotEmpty()) {
            tagService.validateTagIds(userId, tagIds)
        }

        deckTagRepository.setTagsForDeck(deckId, tagIds)

        val tags = deckTagRepository.findTagsByDeckId(deckId)
        return deck.toResponse(tags)
    }
}

/**
 * Exception thrown when a deck is not found.
 */
class DeckNotFoundException(val deckId: UUID) : RuntimeException("Deck not found: $deckId")
