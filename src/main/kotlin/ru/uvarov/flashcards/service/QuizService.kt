package ru.uvarov.flashcards.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.uvarov.flashcards.model.Answer
import ru.uvarov.flashcards.model.DictionaryLine
import ru.uvarov.flashcards.model.Question
import ru.uvarov.flashcards.model.WordPair
import kotlin.math.pow
import kotlin.random.Random

@Service
class QuizService(
    @Value("\${quiz.answer-size}") private val answersSize: Int,
    private val fileService: FileService,
) {

    fun getQuestion(): Question {
        val wordPairs = fileService.wordPairs
        val wordsKeysRu = wordPairs.keys.toMutableList().apply { shuffle() }

        val wordRu = pickWeighted(fileService.wordWeights)
        val translateSrb = wordPairs.getValue(wordRu)

        val answers = mutableListOf(Answer(translateSrb, wordRu, correct = true))
        findAndFillStartingSameLetter(answers, wordsKeysRu, wordPairs, translateSrb)
        fillIfNotEnough(answers, wordsKeysRu, wordPairs)
        answers.shuffle()

        return Question(wordRu, answers)
    }

    fun getDictionaryLines(): List<DictionaryLine> = fileService.getDictionaryLines()

    fun getTypeQuestion(): WordPair {
        val wordPairs = fileService.wordPairs
        val wordRu = pickWeighted(fileService.wordWeights)
        return WordPair(wordRu, wordPairs.getValue(wordRu))
    }

    // Bias toward low/negative weight words. 0.7^weight: weight=-3 -> ~2.9x, 0 -> 1, +5 -> ~0.17, +10 -> ~0.028.
    private fun pickWeighted(weights: Map<String, Int>): String {
        require(weights.isNotEmpty()) { "Dictionary is empty" }
        val scores = weights.mapValues { (_, w) -> 0.7.pow(w.toDouble()) }
        val total = scores.values.sum()
        var r = Random.nextDouble() * total
        for ((word, score) in scores) {
            r -= score
            if (r <= 0.0) return word
        }
        return scores.keys.last()
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
