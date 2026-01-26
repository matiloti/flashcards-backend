package com.flashcards

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlashcardsApplication

fun main(args: Array<String>) {
    runApplication<FlashcardsApplication>(*args)
}
