package ru.uvarov.flashcards.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class Question {
    private String word;
    private List<Answer> answersList;
}
