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

    @Test
    fun `getQuestion - dictionary larger than answers size - returns answers size options with one correct`() {
        val fileService = mock<FileService>()
        whenever(fileService.wordPairs).thenReturn(
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
        val fileService = mock<FileService>()
        whenever(fileService.wordPairs).thenReturn(dict)
        val service = QuizService(ANSWERS_SIZE, fileService)

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
        val fileService = mock<FileService>()
        whenever(fileService.wordPairs).thenReturn(dict)
        val service = QuizService(ANSWERS_SIZE, fileService)

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
        val fileService = mock<FileService>()
        whenever(fileService.wordPairs).thenReturn(
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
    fun `getTypeQuestion - any run - returns existing pair from dictionary`() {
        val dict = mapOf("утром" to "ујутру", "вечер" to "вече")
        val fileService = mock<FileService>()
        whenever(fileService.wordPairs).thenReturn(dict)
        val service = QuizService(ANSWERS_SIZE, fileService)

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
