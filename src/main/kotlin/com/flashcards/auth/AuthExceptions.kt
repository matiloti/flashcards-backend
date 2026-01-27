package com.flashcards.auth

/**
 * Thrown when attempting to register with an email that already exists.
 */
class EmailAlreadyExistsException(email: String) :
    RuntimeException("Email already exists: $email")

/**
 * Thrown when login credentials are invalid.
 * Generic message to prevent account enumeration.
 */
class InvalidCredentialsException :
    RuntimeException("Invalid email or password")

/**
 * Thrown when a refresh token is invalid, expired, or revoked.
 */
class InvalidTokenException(message: String) :
    RuntimeException(message)

/**
 * Thrown when password doesn't meet requirements.
 */
class InvalidPasswordException(message: String) :
    RuntimeException(message)

/**
 * Thrown when display name doesn't meet requirements.
 */
class InvalidDisplayNameException(message: String) :
    RuntimeException(message)
