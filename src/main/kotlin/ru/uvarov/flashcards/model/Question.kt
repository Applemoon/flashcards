package ru.uvarov.flashcards.model

data class Question(
    val word: String,
    val answersList: List<Answer>,
)
