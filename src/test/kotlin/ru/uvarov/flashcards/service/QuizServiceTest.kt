package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.uvarov.flashcards.model.DictionaryLine

private const val ANSWERS_SIZE = 4
private const val RUNS = 100

class QuizServiceTest {

    private fun mockFile(
        pairs: Map<String, String>,
        weights: Map<String, Int>? = null,
        categories: Map<String, String>? = null,
    ): FileService {
        val fs = mock<FileService>()
        whenever(fs.wordPairs).thenReturn(pairs)
        whenever(fs.wordWeights).thenReturn(weights ?: pairs.keys.associateWith { 0 })
        whenever(fs.wordCategories).thenReturn(categories ?: pairs.keys.associateWith { "default" })
        return fs
    }

    private fun isSentence(srb: String) = srb.trim().let { it.endsWith(".") || it.endsWith("?") }

    @Test
    fun `getQuestion - dictionary larger than answers size - returns answers size options with one correct`() {
        val fileService = mockFile(
            mapOf(
                "утром" to "ујутру",
                "вечер" to "вече",
                "ночь" to "ноћ",
                "полночь" to "поноћ",
                "вчера" to "јуче",
            )
        )
        val service = QuizService(ANSWERS_SIZE, fileService)

        repeat(RUNS) {
            val question = service.getQuestion()
            assertEquals(ANSWERS_SIZE, question.answersList.size)
            assertEquals(1, question.answersList.count { it.correct })
        }
    }

    @Test
    fun `getQuestion - any run - correct answer matches question word`() {
        val dict = mapOf(
            "утром" to "ујутру",
            "вечер" to "вече",
            "ночь" to "ноћ",
            "полночь" to "поноћ",
        )
        val service = QuizService(ANSWERS_SIZE, mockFile(dict))

        repeat(RUNS) {
            val question = service.getQuestion()
            val correct = question.answersList.single { it.correct }
            assertEquals(question.word, correct.translate)
            assertEquals(dict[question.word], correct.word)
        }
    }

    @Test
    fun `getQuestion - enough same-type entries - all distractors match correct type`() {
        // 4 одиночных слова и 4 предложения: каждого типа хватает, чтобы заполнить варианты.
        val dict = mapOf(
            "с1" to "aa", "с2" to "bb", "с3" to "cc", "с4" to "dd",
            "в1" to "p q r s?", "в2" to "t u v w?", "в3" to "x y z a?", "в4" to "b c d e?",
        )
        val service = QuizService(ANSWERS_SIZE, mockFile(dict))

        repeat(RUNS) {
            val question = service.getQuestion()
            val correct = question.answersList.single { it.correct }
            assertTrue(
                question.answersList.all { isSentence(it.word) == isSentence(correct.word) },
                "Все варианты должны быть одного типа с правильным, но получили: ${question.answersList}",
            )
        }
    }

    @Test
    fun `getQuestion - enough same-category words - all distractors share category`() {
        val dict = mapOf(
            "a1" to "x1", "a2" to "x2", "a3" to "x3", "a4" to "x4",
            "b1" to "y1", "b2" to "y2", "b3" to "y3", "b4" to "y4",
        )
        val categories = dict.keys.associateWith { if (it.startsWith("a")) "A" else "B" }
        val service = QuizService(ANSWERS_SIZE, mockFile(dict, categories = categories))

        repeat(RUNS) {
            val question = service.getQuestion()
            val correctCat = categories.getValue(question.word)
            assertTrue(
                question.answersList.all { categories.getValue(it.translate) == correctCat },
                "Все варианты должны быть из категории '$correctCat', но получили: ${question.answersList}",
            )
        }
    }

    @Test
    fun `getQuestion - enough same-letter candidates - prefers distractors starting with same letter`() {
        // Одна категория, один тип — приоритет первой буквы решает порядок внутри тира.
        val dict = mapOf(
            "слово1" to "ujutru",
            "слово2" to "uvek",
            "слово3" to "uveče",
            "слово4" to "užina",
            "чужое1" to "veče",
            "чужое2" to "noć",
        )
        val service = QuizService(ANSWERS_SIZE, mockFile(dict))

        repeat(RUNS) {
            val question = service.getQuestion()
            val correct = question.answersList.single { it.correct }
            if (correct.word.startsWith("u")) {
                assertTrue(
                    question.answersList.all { it.word.startsWith("u") },
                    "При верном на 'u' все варианты должны начинаться на 'u', получили: ${question.answersList}",
                )
            }
        }
    }

    @Test
    fun `getQuestion - same-type pool insufficient - falls back to fill answers size`() {
        // Один-единственный sentence-вопрос некем добить по типу — добор уходит в тир «что угодно».
        val dict = mapOf(
            "слово1" to "ujutru",
            "слово2" to "veče",
            "слово3" to "ноћ",
            "вопрос" to "како је?",
        )
        val service = QuizService(ANSWERS_SIZE, mockFile(dict))

        repeat(RUNS) {
            val question = service.getQuestion()
            assertEquals(ANSWERS_SIZE, question.answersList.size)
            assertEquals(1, question.answersList.count { it.correct })
        }
    }

    @Test
    fun `getQuestion - one word has strongly negative weight - is picked far more often`() {
        val dict = (1..10).associate { "слово$it" to "perevod$it" }
        val weights = dict.keys.associateWith { 0 }.toMutableMap()
        weights["слово1"] = -8
        val service = QuizService(ANSWERS_SIZE, mockFile(dict, weights))

        val runs = 500
        val counts = mutableMapOf<String, Int>()
        repeat(runs) {
            val q = service.getQuestion()
            counts.merge(q.word, 1) { acc, _ -> acc + 1 }
        }
        // 0.7^-8 ≈ 17.5 vs 1 для остальных, доля «слово1» должна быть существенно выше 1/10.
        val uniformShare = 1.0 / dict.size
        val actualShare = (counts["слово1"] ?: 0).toDouble() / runs
        assertTrue(
            actualShare > uniformShare * 3,
            "Слово с весом -8 должно появляться чаще равномерного в >3 раза, было $actualShare vs $uniformShare",
        )
    }

    @Test
    fun `getTypeQuestion - any run - returns existing pair from dictionary`() {
        val dict = mapOf("утром" to "ујутру", "вечер" to "вече")
        val service = QuizService(ANSWERS_SIZE, mockFile(dict))

        repeat(RUNS) {
            val pair = service.getTypeQuestion()
            assertNotNull(pair.wordRu)
            assertEquals(dict[pair.wordRu], pair.wordSrb)
        }
    }

    @Test
    fun `getDictionaryLines - service returns lines - proxies through`() {
        val content = listOf(
            DictionaryLine.Heading("#Категория"),
            DictionaryLine.Word("утром", "ујутру", 0),
        )
        val fileService = mock<FileService>()
        whenever(fileService.getDictionaryLines()).thenReturn(content)
        val service = QuizService(ANSWERS_SIZE, fileService)

        assertEquals(content, service.getDictionaryLines())
    }
}
