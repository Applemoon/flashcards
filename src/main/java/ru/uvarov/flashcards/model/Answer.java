package ru.uvarov.flashcards.model;


import java.util.Objects;

public record Answer(
    String word,
    String translate,
    boolean isRight
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Answer answer = (Answer) o;
        return word.equals(answer.word) && translate.equals(answer.translate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, translate);
    }
}
