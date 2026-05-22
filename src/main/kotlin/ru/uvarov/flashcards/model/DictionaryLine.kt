package ru.uvarov.flashcards.model

sealed interface DictionaryLine {
    val kind: String

    data class Word(
        val ru: String,
        val srb: String,
        val weight: Int,
    ) : DictionaryLine {
        override val kind: String = "word"
    }

    data class Heading(
        val text: String,
    ) : DictionaryLine {
        override val kind: String = "heading"
    }

    data object Blank : DictionaryLine {
        override val kind: String = "blank"
    }
}
