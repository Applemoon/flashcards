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

    private static final int ANSWERS_SIZE = 6;

    final private Map<String, String> wordPairsMap;

    public QuizService(FileService fileService) {
        wordPairsMap = fileService.readPairs();
        System.out.println("Найдено " + wordPairsMap.size() + " слов");
        assert wordPairsMap.size() >= ANSWERS_SIZE;
    }

    public Question getQuestion() {
        List<Answer> answersList = new ArrayList<>(ANSWERS_SIZE);

        List<String> wordsKeys = new ArrayList<>(wordPairsMap.keySet());
        Collections.shuffle(wordsKeys);

        final String word = wordsKeys.get(0);
        answersList.add(new Answer(wordPairsMap.get(word), word, true));

        for (int i = 1; i < ANSWERS_SIZE; i++) {
            final String answer = wordsKeys.get(i);
            answersList.add(new Answer(wordPairsMap.get(answer), answer, false));
        }

        Collections.shuffle(answersList);
        System.out.println(word);
        return new Question(word, answersList);
    }
}
