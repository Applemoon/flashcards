package ru.uvarov.flashcards.service;

import org.springframework.stereotype.Service;
import ru.uvarov.flashcards.model.Pair;
import ru.uvarov.flashcards.model.Question;

import java.util.List;

@Service
public class QuizService {
    public Question getQuestion() {
        return new Question(
            "tata",
            List.of(
                new Pair("папа", true),
                new Pair("яйцо", false),
                new Pair("шляпа", false),
                new Pair("ретузы", false)
            )
        );
    }
}
