package ru.uvarov.flashcards.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.uvarov.flashcards.model.Answer
import ru.uvarov.flashcards.model.Question
import ru.uvarov.flashcards.model.WordPair

@Service
class QuizService(
    @Value("\${quiz.answer-size}") private val answersSize: Int,
    private val fileService: FileService,
) {

    fun getQuestion(): Question {
        val wordPairs = fileService.wordPairs
        val wordsKeysRu = wordPairs.keys.toMutableList().apply { shuffle() }

        val wordRu = wordsKeysRu.first()
        val translateSrb = wordPairs.getValue(wordRu)

        val answers = mutableListOf(Answer(translateSrb, wordRu, correct = true))
        findAndFillStartingSameLetter(answers, wordsKeysRu, wordPairs, translateSrb)
        fillIfNotEnough(answers, wordsKeysRu, wordPairs)
        answers.shuffle()

        return Question(wordRu, answers)
    }

    fun getAllWords(): List<String> = fileService.fileContent

    fun getTypeQuestion(): WordPair {
        val wordPairs = fileService.wordPairs
        val wordRu = wordPairs.keys.random()
        return WordPair(wordRu, wordPairs.getValue(wordRu))
    }

    private fun findAndFillStartingSameLetter(
        answers: MutableList<Answer>,
        wordsKeysRu: List<String>,
        wordPairs: Map<String, String>,
        translateSrb: String,
    ) {
        val firstLetter = translateSrb.substring(0, 1)
        for (currentWordRu in wordsKeysRu) {
            if (answers.size >= answersSize) return
            val currentAnswerSrb = wordPairs.getValue(currentWordRu)
            if (currentAnswerSrb != translateSrb && currentAnswerSrb.startsWith(firstLetter)) {
                answers += Answer(currentAnswerSrb, currentWordRu, correct = false)
            }
        }
    }

    private fun fillIfNotEnough(
        answers: MutableList<Answer>,
        wordsKeysRu: List<String>,
        wordPairs: Map<String, String>,
    ) {
        for (answerRu in wordsKeysRu) {
            if (answers.size >= answersSize) return
            val candidate = Answer(wordPairs.getValue(answerRu), answerRu, correct = false)
            if (candidate !in answers) {
                answers += candidate
            }
        }
    }
}
