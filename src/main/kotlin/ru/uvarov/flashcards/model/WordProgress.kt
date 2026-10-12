package ru.uvarov.flashcards.model

import java.time.LocalDate

// Состояние слова в расписании показов: память по FSRS и даты. У новых слов записи нет.
data class WordProgress(
    val stability: Double,
    val difficulty: Double,
    val lastReview: LocalDate,
    val due: LocalDate,
)
