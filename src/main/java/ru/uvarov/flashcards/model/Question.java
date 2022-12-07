package ru.uvarov.flashcards.model;

import java.util.List;

public record Question(
    String word,
    List<Answer> answersList
) {
}
