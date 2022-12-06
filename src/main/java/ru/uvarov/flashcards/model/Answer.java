package ru.uvarov.flashcards.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Answer {
    private String word;
    private Boolean isRight;

    public Answer(String word) {
        this.word = word;
        this.isRight = false;
    }
}
