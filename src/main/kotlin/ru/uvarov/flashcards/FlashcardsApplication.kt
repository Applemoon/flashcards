package ru.uvarov.flashcards

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.io.IOException
import java.time.Clock

@SpringBootApplication
class FlashcardsApplication {

    // «Сегодня» для расписания показов; в тестах подменяется фиксированными часами.
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}

private val log = LoggerFactory.getLogger(FlashcardsApplication::class.java)

fun main(args: Array<String>) {
    runApplication<FlashcardsApplication>(*args)
    try {
        ProcessBuilder("open", "http://localhost:8080").start()
    } catch (e: IOException) {
        log.error("Failed to run browser", e)
    }
}
