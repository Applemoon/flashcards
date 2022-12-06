package ru.uvarov.flashcards.service;

import org.springframework.stereotype.Service;
import ru.uvarov.flashcards.model.Answer;
import ru.uvarov.flashcards.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class QuizService {

    private static final int ANSWERS_SIZE = 4;

    final private Map<String, String> pairsMap;

    public QuizService(FileService fileService) {
        pairsMap = fileService.readPairs();
        assert pairsMap.size() >= ANSWERS_SIZE;
    }

    public Question getQuestion() {
        List<Answer> answersList = new ArrayList<>(ANSWERS_SIZE);

        List<String> keys = new ArrayList<>(pairsMap.keySet());
        Collections.shuffle(keys);

        final String word = keys.get(0);
        answersList.add(new Answer(pairsMap.get(word), true));

        for (int i = 1; i < ANSWERS_SIZE; i++) {
            final String answer = keys.get(i);
            answersList.add(new Answer(pairsMap.get(answer)));
        }

        Collections.shuffle(answersList);
        return new Question(word, answersList);
    }
}
