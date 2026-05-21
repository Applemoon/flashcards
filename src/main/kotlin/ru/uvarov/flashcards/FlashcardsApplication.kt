package ru.uvarov.flashcards

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.IOException

@SpringBootApplication
class FlashcardsApplication

private val log = LoggerFactory.getLogger(FlashcardsApplication::class.java)

fun main(args: Array<String>) {
    runApplication<FlashcardsApplication>(*args)
    try {
        ProcessBuilder("open", "http://localhost:8080").start()
    } catch (e: IOException) {
        log.error("Failed to run browser", e)
    }
}
