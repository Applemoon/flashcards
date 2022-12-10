package ru.uvarov.flashcards.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.uvarov.flashcards.model.Answer;
import ru.uvarov.flashcards.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class QuizService {

    final private Map<String, String> wordPairsMap;
    final private Integer answersSize;

    public QuizService(FileService fileService, @Value("${quiz.answer.size}") Integer answerSize) {
        wordPairsMap = fileService.readPairs();
        this.answersSize = answerSize;

        System.out.println("Найдено " + wordPairsMap.size() + " слов");
        assert wordPairsMap.size() >= answersSize;
    }

    public Question getQuestion() {
        List<Answer> answersList = new ArrayList<>(answersSize);

        List<String> wordsKeysRu = new ArrayList<>(wordPairsMap.keySet());
        Collections.shuffle(wordsKeysRu);

        final String wordRu = wordsKeysRu.get(0);
        final String translateSrb = wordPairsMap.get(wordRu);
        answersList.add(new Answer(translateSrb, wordRu, true));

        findAndFillStartingSameLetter(answersList, wordsKeysRu, translateSrb);
        fillIfNotEnough(answersList, wordsKeysRu);

        Collections.shuffle(answersList);
        System.out.println(wordRu);
        return new Question(wordRu, answersList);
    }

    private void findAndFillStartingSameLetter(List<Answer> answersList, List<String> wordsKeysRu, String translateSrb) {
        int i = 0;
        while (answersList.size() < answersSize && i < wordsKeysRu.size()) {
            final String currentWordRu = wordsKeysRu.get(i);
            final String currentAnswerSrb = wordPairsMap.get(currentWordRu);
            final String firstLetter = translateSrb.substring(0, 1);
            if (!currentAnswerSrb.equals(translateSrb) && currentAnswerSrb.startsWith(firstLetter)) {
                answersList.add(new Answer(currentAnswerSrb, currentWordRu, false));
            }
            i++;
        }
    }

    private void fillIfNotEnough(List<Answer> answersList, List<String> wordsKeysRu) {
        int i = 0;
        while (answersList.size() < answersSize) {
            final String answerRu = wordsKeysRu.get(i);
            answersList.add(new Answer(wordPairsMap.get(answerRu), answerRu, false));
        }
    }
}
