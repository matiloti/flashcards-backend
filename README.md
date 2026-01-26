# Flashcards Backend

Kotlin Spring Boot REST API for the Flashcards app.

## Tech Stack

- Kotlin + Spring Boot 3.2
- Spring JDBC (JdbcTemplate)
- PostgreSQL 16
- Flyway (migrations)
- Testcontainers + JUnit 5 (testing)

## Prerequisites

- JDK 17+
- Docker (for tests and local PostgreSQL)

## Running Locally

Start a PostgreSQL instance:

```bash
docker run -d --name flashcards-db \
  -e POSTGRES_DB=flashcards \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16
```

Run the application:

```bash
./gradlew bootRun
```

The API will be available at `http://localhost:8080`.

## Running Tests

Requires Docker running (Testcontainers spins up PostgreSQL automatically):

```bash
./gradlew test
```

## API Endpoints

### Decks

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/decks` | List all decks |
| POST | `/api/v1/decks` | Create a deck |
| GET | `/api/v1/decks/{id}` | Get deck by ID |
| PUT | `/api/v1/decks/{id}` | Update a deck |
| DELETE | `/api/v1/decks/{id}` | Delete a deck |

### Cards

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/decks/{deckId}/cards` | List cards in deck |
| POST | `/api/v1/decks/{deckId}/cards` | Create a card |
| GET | `/api/v1/decks/{deckId}/cards/{id}` | Get card by ID |
| PUT | `/api/v1/decks/{deckId}/cards/{id}` | Update a card |
| DELETE | `/api/v1/decks/{deckId}/cards/{id}` | Delete a card |

### Study Sessions

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/decks/{deckId}/study` | Start a study session |
| POST | `/api/v1/study/{sessionId}/reviews` | Submit a card review |
| POST | `/api/v1/study/{sessionId}/complete` | Complete a session |

## Project Structure

```
src/main/kotlin/com/flashcards/
├── FlashcardsApplication.kt
├── deck/
│   ├── Deck.kt
│   ├── DeckController.kt
│   ├── DeckRepository.kt
│   ├── CreateDeckRequest.kt
│   └── UpdateDeckRequest.kt
├── card/
│   ├── Card.kt
│   ├── CardController.kt
│   ├── CardRepository.kt
│   ├── CreateCardRequest.kt
│   └── UpdateCardRequest.kt
└── study/
    ├── StudySession.kt
    ├── StudyController.kt
    └── StudyRepository.kt
```
