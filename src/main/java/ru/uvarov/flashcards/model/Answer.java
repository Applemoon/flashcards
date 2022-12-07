package ru.uvarov.flashcards.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Answer {
    private String word;
    private String translate;
    private Boolean isRight;

    public Answer(String word, String translate) {
        this.word = word;
        this.translate = translate;
        this.isRight = false;
    }
}
