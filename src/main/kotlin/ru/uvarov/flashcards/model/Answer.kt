package ru.uvarov.flashcards.model

import java.util.Objects

data class Answer(
    val word: String,
    val translate: String,
    val correct: Boolean,
) {
    // equals/hashCode игнорируют correct: дедупликация в QuizService.fillIfNotEnough
    // должна считать «правильный» и «случайно совпавший» одним и тем же ответом.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Answer) return false
        return word == other.word && translate == other.translate
    }

    override fun hashCode(): Int = Objects.hash(word, translate)
}
