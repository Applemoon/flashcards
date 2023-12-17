package ru.uvarov.flashcards.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.uvarov.flashcards.model.Answer;
import ru.uvarov.flashcards.model.Pair;
import ru.uvarov.flashcards.model.Question;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class QuizService {

    final private Integer answersSize;
    final private FileService fileService;

    public QuizService(@Value("${quiz.answer.size}") Integer answersSize, FileService fileService) {
        this.answersSize = answersSize;
        this.fileService = fileService;
    }

    public Question getQuestion() {
        final List<Answer> answersList = new ArrayList<>(answersSize);

        final List<String> wordsKeysRu = new ArrayList<>(fileService.getWordPairs().keySet());
        Collections.shuffle(wordsKeysRu);

        final String wordRu = wordsKeysRu.getFirst();
        final String translateSrb = fileService.getWordPairs().get(wordRu);
        answersList.add(new Answer(translateSrb, wordRu, true));

        findAndFillStartingSameLetter(answersList, wordsKeysRu, translateSrb);
        fillIfNotEnough(answersList, wordsKeysRu);

        Collections.shuffle(answersList);
        return new Question(wordRu, answersList);
    }

    public List<String> getAllWords() {
        return fileService.getFileContent();
    }

    public Pair getTypeQuestion() {
        final Map<String, String> wordPairs = fileService.getWordPairs();
        final List<String> wordsKeys = new ArrayList<>(wordPairs.keySet());
        final int randomIndex = new Random().nextInt(wordsKeys.size());
        final String wordRu = wordsKeys.get(randomIndex);
        return new Pair(wordRu, wordPairs.get(wordRu));
    }

    private void findAndFillStartingSameLetter(List<Answer> answersList, List<String> wordsKeysRu, String translateSrb) {
        int i = 0;
        while (answersList.size() < answersSize && i < wordsKeysRu.size()) {
            final String currentWordRu = wordsKeysRu.get(i);
            final String currentAnswerSrb = fileService.getWordPairs().get(currentWordRu);
            final String firstLetter = translateSrb.substring(0, 1);
            if (!currentAnswerSrb.equals(translateSrb) && currentAnswerSrb.startsWith(firstLetter)) {
                answersList.add(new Answer(currentAnswerSrb, currentWordRu, false));
            }
            i++;
        }
    }

    private void fillIfNotEnough(List<Answer> answersList, List<String> wordsKeysRu) {
        int i = 0;
        while (answersList.size() < answersSize && i < wordsKeysRu.size()) {
            final String answerRu = wordsKeysRu.get(i);
            final Answer answer = new Answer(fileService.getWordPairs().get(answerRu), answerRu, false);
            if (!answersList.contains(answer)) {
                answersList.add(answer);
            }
            i++;
        }
    }
}
