package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private const val ANSWERS_SIZE = 4
private const val RUNS = 100

class QuizServiceTest {

    private fun mockFile(pairs: Map<String, String>, weights: Map<String, Int>? = null): FileService {
        val fs = mock<FileService>()
        whenever(fs.wordPairs).thenReturn(pairs)
        whenever(fs.wordWeights).thenReturn(weights ?: pairs.keys.associateWith { 0 })
        return fs
    }

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
    fun `getQuestion - correct serbian starts with given letter - prefers distractors starting with same letter`() {
        val dict = mapOf(
            "слово1" to "ujutru",
            "слово2" to "uvek",
            "слово3" to "uveče",
            "слово4" to "užina",
            "чужое" to "veče",
        )
        val service = QuizService(ANSWERS_SIZE, mockFile(dict))

        repeat(RUNS) {
            val question = service.getQuestion()
            val correct = question.answersList.single { it.correct }
            if (correct.word.startsWith("u")) {
                assertTrue(
                    question.answersList.all { it.word.startsWith("u") },
                    "Все ответы должны начинаться на 'u', но получили: ${question.answersList}",
                )
            }
        }
    }

    @Test
    fun `getQuestion - same letter pool insufficient - falls back to random`() {
        val fileService = mockFile(
            mapOf(
                "слово1" to "ujutru",
                "слово2" to "veče",
                "слово3" to "ноћ",
                "слово4" to "spavanje",
            )
        )
        val service = QuizService(ANSWERS_SIZE, fileService)

        repeat(RUNS) {
            val question = service.getQuestion()
            assertEquals(ANSWERS_SIZE, question.answersList.size)
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
    fun `getAllWords - service returns content - proxies through`() {
        val content = listOf("#Категория", "утром=ујутру")
        val fileService = mock<FileService>()
        whenever(fileService.fileContent).thenReturn(content)
        val service = QuizService(ANSWERS_SIZE, fileService)

        assertEquals(content, service.getAllWords())
    }
}
