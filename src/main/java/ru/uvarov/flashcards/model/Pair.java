package ru.uvarov.flashcards.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Pair {
    private String word;
    private Boolean isRight;
}
