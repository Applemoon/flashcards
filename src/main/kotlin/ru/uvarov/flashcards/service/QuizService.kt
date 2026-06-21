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
        val categories = fileService.wordCategories
        val types = wordPairs.mapValues { (_, srb) -> classify(srb) }
        val keys = wordPairs.keys.shuffled()

        val wordRu = pickWeighted(fileService.wordWeights)
        val translateSrb = wordPairs.getValue(wordRu)
        val targetType = types.getValue(wordRu)
        val targetCategory = categories[wordRu]

        // Внутри каждого тира слова на ту же букву, что и верный перевод, идут первыми.
        // keys уже перемешаны, а sortedByDescending стабильна — случайность внутри групп сохраняется.
        val firstLetter = translateSrb.firstOrNull()
        val prioritized = keys.sortedByDescending {
            firstLetter != null && wordPairs.getValue(it).startsWith(firstLetter)
        }

        val answers = mutableListOf(Answer(translateSrb, wordRu, correct = true))
        // Дистракторы добираются тремя тирами, от самого сильного к запасному:
        // 1) та же категория и тот же тип записи (слово/фраза/предложение),
        // 2) тот же тип в любой категории, 3) что угодно — лишь бы набрать answersSize.
        fill(answers, prioritized, wordPairs) { ru -> categories[ru] == targetCategory && types.getValue(ru) == targetType }
        fill(answers, prioritized, wordPairs) { ru -> types.getValue(ru) == targetType }
        fill(answers, prioritized, wordPairs) { true }
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

    // Добавляет дистракторы, проходящие accept, пока не наберётся answersSize.
    // Дубликаты (в т.ч. совпавший правильный ответ) отсеиваются через Answer.equals.
    private fun fill(
        answers: MutableList<Answer>,
        keys: List<String>,
        wordPairs: Map<String, String>,
        accept: (String) -> Boolean,
    ) {
        for (ru in keys) {
            if (answers.size >= answersSize) return
            if (!accept(ru)) continue
            val candidate = Answer(wordPairs.getValue(ru), ru, correct = false)
            if (candidate !in answers) {
                answers += candidate
            }
        }
    }

    // Тип записи по сербской стороне (то, что видно как вариант ответа): одиночное слово,
    // короткая фраза или предложение. Удерживает квиз от смешивания «мачка» с «боли ме глава.».
    private fun classify(srb: String): WordType {
        val s = srb.trim()
        if (s.endsWith(".") || s.endsWith("?")) return WordType.SENTENCE
        val tokens = s.split(Regex("[ ,]+")).filter { it.isNotBlank() }
        return when {
            tokens.size >= 4 -> WordType.SENTENCE
            tokens.size >= 2 -> WordType.PHRASE
            else -> WordType.WORD
        }
    }

    private enum class WordType { WORD, PHRASE, SENTENCE }
}
