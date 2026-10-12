package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ru.uvarov.flashcards.model.DictionaryLine
import ru.uvarov.flashcards.model.WordProgress
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

private const val ANSWERS_SIZE = 4
private const val RUNS = 100
private const val RETRY_AFTER = 3
private val TODAY = LocalDate.of(2026, 10, 12)
private val CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

class QuizServiceTest {

    private fun mockFile(
        pairs: Map<String, String>,
        categories: Map<String, String>? = null,
    ): FileService {
        val fs = mock<FileService>()
        whenever(fs.wordPairs).thenReturn(pairs)
        whenever(fs.wordCategories).thenReturn(categories ?: pairs.keys.associateWith { "default" })
        return fs
    }

    private fun mockProgress(progress: Map<String, WordProgress> = emptyMap()): ProgressService {
        val ps = mock<ProgressService>()
        whenever(ps.progress).thenReturn(progress)
        return ps
    }

    private fun quizService(fileService: FileService, progressService: ProgressService = mockProgress()) =
        QuizService(ANSWERS_SIZE, RETRY_AFTER, fileService, progressService, CLOCK)

    // Слово, отвеченное lastDaysAgo дней назад, со сроком через dueInDays дней (отрицательный — просрочено).
    private fun reviewed(stability: Double, lastDaysAgo: Long, dueInDays: Long) =
        WordProgress(stability, 5.0, TODAY.minusDays(lastDaysAgo), TODAY.plusDays(dueInDays))

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
        val service = quizService(fileService)

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
        val service = quizService(mockFile(dict))

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
        val service = quizService(mockFile(dict))

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
        val service = quizService(mockFile(dict, categories = categories))

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
        val service = quizService(mockFile(dict))

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
        val service = quizService(mockFile(dict))

        repeat(RUNS) {
            val question = service.getQuestion()
            assertEquals(ANSWERS_SIZE, question.answersList.size)
            assertEquals(1, question.answersList.count { it.correct })
        }
    }

    @Test
    fun `getQuestion - due word among new ones - due word asked first`() {
        val dict = (1..10).associate { "слово$it" to "perevod$it" }
        val service = quizService(mockFile(dict), mockProgress(mapOf("слово7" to reviewed(2.0, 3, -1))))

        assertEquals("слово7", service.getQuestion().word)
    }

    @Test
    fun `getQuestion - several due words - least retrievable first`() {
        val dict = (1..10).associate { "слово$it" to "perevod$it" }
        val progress = mapOf(
            "слово2" to reviewed(stability = 30.0, lastDaysAgo = 30, dueInDays = 0),
            "слово5" to reviewed(stability = 2.0, lastDaysAgo = 10, dueInDays = -8),
        )
        val service = quizService(mockFile(dict), mockProgress(progress))

        assertEquals("слово5", service.getQuestion().word)
        assertEquals("слово2", service.getQuestion().word)
    }

    @Test
    fun `getQuestion - nothing due - only new words asked`() {
        val dict = (1..10).associate { "слово$it" to "perevod$it" }
        val service = quizService(mockFile(dict), mockProgress(mapOf("слово1" to reviewed(10.0, 1, 9))))

        repeat(RUNS) {
            assertTrue(service.getQuestion().word != "слово1")
        }
    }

    @Test
    fun `getQuestion - no due and no new words - least retrievable asked ahead of schedule`() {
        val dict = (1..6).associate { "слово$it" to "perevod$it" }
        val progress = dict.keys.associateWith { reviewed(100.0, 1, 99) } + ("слово4" to reviewed(3.0, 2, 1))
        val service = quizService(mockFile(dict), mockProgress(progress))

        assertEquals("слово4", service.getQuestion().word)
    }

    @Test
    fun `getQuestion - skipped due word - not asked again within retryAfter questions`() {
        val dict = (1..10).associate { "слово$it" to "perevod$it" }
        val service = quizService(mockFile(dict), mockProgress(mapOf("слово3" to reviewed(2.0, 5, -3))))

        assertEquals("слово3", service.getQuestion().word)
        repeat(RETRY_AFTER) { assertTrue(service.getQuestion().word != "слово3") }
        assertEquals("слово3", service.getQuestion().word)
    }

    @Test
    fun `getQuestion - due count - only dictionary words due today or earlier`() {
        val dict = (1..5).associate { "слово$it" to "perevod$it" }
        val progress = mapOf(
            "слово1" to reviewed(2.0, 2, 0),
            "слово2" to reviewed(2.0, 4, -2),
            "слово3" to reviewed(2.0, 1, 1),
            "удалённое" to reviewed(2.0, 4, -2),
        )
        val service = quizService(mockFile(dict), mockProgress(progress))

        assertEquals(2, service.getQuestion().dueCount)
    }

    @Test
    fun `getQuestion - sentence and single word - sentence flag and latin answer set`() {
        val service = quizService(mockFile(mapOf("болит голова" to "боли ме глава.")))
        val sentence = service.getQuestion()
        assertTrue(sentence.sentence)
        assertEquals("boli me glava.", sentence.answerLatin)

        val word = quizService(mockFile(mapOf("черепаха" to "корњача"))).getQuestion()
        assertFalse(word.sentence)
        assertEquals("kornjača", word.answerLatin)
    }

    @Test
    fun `checkTyped - correct or accent - recorded as correct`() {
        val fileService = mockFile(mapOf("черепаха" to "корњача", "ящерица" to "гуштер"))
        val progressService = mockProgress()
        val service = quizService(fileService, progressService)

        assertEquals(AnswerChecker.Verdict.CORRECT, service.checkTyped("черепаха", "корњача"))
        assertEquals(AnswerChecker.Verdict.ACCENT, service.checkTyped("ящерица", "guster"))

        verify(fileService).recordAnswer("черепаха", true)
        verify(fileService).recordAnswer("ящерица", true)
        verify(progressService).record("черепаха", Fsrs.Rating.GOOD, TODAY)
    }

    @Test
    fun `checkTyped - wrong or blank - recorded as wrong and queued for retry`() {
        val (service, fileService) = retryFixture()

        assertEquals(AnswerChecker.Verdict.WRONG, service.checkTyped("слово2", ""))

        verify(fileService).recordAnswer("слово2", false)
        val questions = (1..RETRY_AFTER).map { service.getQuestion() }
        assertEquals("слово2", questions.last().word)
    }

    @Test
    fun `checkTyped - unknown word - throws IllegalArgumentException`() {
        val (service, _) = retryFixture()

        assertThrows(IllegalArgumentException::class.java) { service.checkTyped("нет такого", "x") }
    }

    @Test
    fun `getDictionaryLines - service returns lines - proxies through`() {
        val content = listOf(
            DictionaryLine.Heading("Категория"),
            DictionaryLine.Word("утром", "ујутру", 0),
        )
        val fileService = mock<FileService>()
        whenever(fileService.getDictionaryLines()).thenReturn(content)
        val service = quizService(fileService)

        assertEquals(content, service.getDictionaryLines())
    }

    // Все слова новые, поэтому выбор случайный; «слово2» с меткой retry — заслуга очереди повторов.
    private fun retryFixture(progressService: ProgressService = mockProgress()): Pair<QuizService, FileService> {
        val dict = (1..50).associate { "слово$it" to "perevod$it" }
        val fileService = mockFile(dict)
        return quizService(fileService, progressService) to fileService
    }

    @Test
    fun `recordAnswer - wrong - word comes back as retry after retryAfter questions`() {
        val (service, fileService) = retryFixture()
        service.recordAnswer("слово2", correct = false, recall = false)

        val questions = (1..RETRY_AFTER).map { service.getQuestion() }

        verify(fileService).recordAnswer("слово2", false)
        questions.dropLast(1).forEach { assertFalse(it.retry, "Повтор пришёл раньше срока: ${it.word}") }
        assertEquals("слово2", questions.last().word)
        assertTrue(questions.last().retry)
    }

    @Test
    fun `recordAnswer - correct on retry question - weight and schedule untouched`() {
        val progressService = mockProgress()
        val (service, fileService) = retryFixture(progressService)
        service.recordAnswer("слово2", correct = false, recall = false)
        repeat(RETRY_AFTER) { service.getQuestion() }

        service.recordAnswer("слово2", correct = true, recall = false)

        verify(fileService, never()).recordAnswer("слово2", true)
        verify(progressService, never()).record("слово2", Fsrs.Rating.HARD, TODAY)
    }

    @Test
    fun `recordAnswer - correct on regular question - forwarded to file service`() {
        val (service, fileService) = retryFixture()
        service.getQuestion()

        service.recordAnswer("слово2", correct = true, recall = false)

        verify(fileService).recordAnswer("слово2", true)
    }

    @Test
    fun `recordAnswer - wrong - rated again`() {
        val progressService = mockProgress()
        val (service, _) = retryFixture(progressService)

        service.recordAnswer("слово2", correct = false, recall = true)

        verify(progressService).record("слово2", Fsrs.Rating.AGAIN, TODAY)
    }

    @Test
    fun `recordAnswer - correct choice from options - rated hard`() {
        val progressService = mockProgress()
        val (service, _) = retryFixture(progressService)

        service.recordAnswer("слово2", correct = true, recall = false)

        verify(progressService).record("слово2", Fsrs.Rating.HARD, TODAY)
    }

    @Test
    fun `recordAnswer - correct recall - rated good`() {
        val progressService = mockProgress()
        val (service, _) = retryFixture(progressService)

        service.recordAnswer("слово2", correct = true, recall = true)

        verify(progressService).record("слово2", Fsrs.Rating.GOOD, TODAY)
    }

    @Test
    fun `getQuestion - retry word deleted from dictionary - dropped from queue`() {
        val (service, fileService) = retryFixture()
        service.recordAnswer("слово2", correct = false, recall = false)
        val pairsLeft = fileService.wordPairs - "слово2"
        whenever(fileService.wordPairs).thenReturn(pairsLeft)

        repeat(RETRY_AFTER * 2) {
            val question = service.getQuestion()
            assertFalse(question.retry)
        }
    }
}
