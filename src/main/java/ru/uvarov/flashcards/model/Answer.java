package ru.uvarov.flashcards.model;


public record Answer(
    String word,
    String translate,
    boolean isRight
) {
}
