package ru.uvarov.flashcards.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.uvarov.flashcards.model.Answer
import ru.uvarov.flashcards.model.DictionaryLine
import ru.uvarov.flashcards.model.Question
import ru.uvarov.flashcards.model.WordProgress
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class QuizService(
    @Value("\${quiz.answer-size}") private val answersSize: Int,
    @Value("\${quiz.retry-after}") private val retryAfter: Int,
    private val fileService: FileService,
    private val progressService: ProgressService,
    private val clock: Clock,
) {

    // Слово с ошибкой → сколько вопросов осталось до повтора. Живёт в памяти: после перезапуска
    // очередь пуста, но вес ошибки уже записан в файл.
    private val retryQueue = LinkedHashMap<String, Int>()

    // Слово, выданное повтором: верный ответ на него вес и расписание не трогает, иначе ошибка снова стирается.
    private var currentRetry: String? = null

    // Последние заданные слова. Не возвращаются раньше, чем через retryAfter вопросов, как и ошибки:
    // иначе пропущенное «самое забытое» слово выпадало бы снова сразу.
    private val recent = ArrayDeque<String>()

    @Synchronized
    fun getQuestion(): Question {
        val wordPairs = fileService.wordPairs
        val categories = fileService.wordCategories
        val types = wordPairs.mapValues { (_, srb) -> classify(srb) }
        val keys = wordPairs.keys.shuffled()

        val today = LocalDate.now(clock)
        val progress = progressService.progress

        currentRetry = nextRetry(wordPairs.keys)
        val wordRu = currentRetry ?: pickScheduled(wordPairs.keys, progress, today)
        recent.addLast(wordRu)
        if (recent.size > retryAfter) recent.removeFirst()
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

        return Question(
            wordRu,
            answers,
            retry = currentRetry != null,
            sentence = targetType == WordType.SENTENCE,
            answerLatin = AnswerChecker.toLatin(translateSrb),
            dueCount = wordPairs.keys.count { progress[it]?.due?.isAfter(today) == false },
        )
    }

    // Один ответ на вопрос: фронтенд шлёт только первый клик. recall — перевод вспомнен без вариантов
    // («Ввод», «Карточки»): для расписания это «хорошо», а верный выбор из вариантов — только «трудно»,
    // узнать легче, чем вспомнить, и интервал от него растёт медленнее.
    @Synchronized
    fun recordAnswer(wordRu: String, correct: Boolean, recall: Boolean) {
        val wasRetry = wordRu == currentRetry
        currentRetry = null
        if (correct && wasRetry) return
        fileService.recordAnswer(wordRu, correct)
        val rating = when {
            !correct -> Fsrs.Rating.AGAIN
            recall -> Fsrs.Rating.GOOD
            else -> Fsrs.Rating.HARD
        }
        progressService.record(wordRu, rating, LocalDate.now(clock))
        if (!correct) retryQueue[wordRu] = retryAfter
    }

    // Ответ из «Ввода»: сверка на сервере и запись как обычного ответа. Без диакритики — засчитывается.
    @Synchronized
    fun checkTyped(wordRu: String, input: String): AnswerChecker.Verdict {
        val expected = requireNotNull(fileService.wordPairs[wordRu]) { "Unknown word: $wordRu" }
        val verdict = AnswerChecker.check(input, expected)
        recordAnswer(wordRu, verdict != AnswerChecker.Verdict.WRONG, recall = true)
        return verdict
    }

    private fun nextRetry(knownWords: Set<String>): String? {
        retryQueue.keys.retainAll(knownWords) // удалённые и переименованные слова выпадают из очереди
        retryQueue.replaceAll { _, left -> left - 1 }
        val due = retryQueue.entries.firstOrNull { it.value <= 0 }?.key ?: return null
        retryQueue.remove(due)
        return due
    }

    fun getDictionaryLines(): List<DictionaryLine> = fileService.getDictionaryLines()

    // Сначала слова, которым подошёл срок, — самое вероятно забытое первым; потом новые, в случайном
    // порядке; когда нет ни тех, ни других, досрочно идёт самое вероятно забытое из остальных.
    private fun pickScheduled(words: Set<String>, progress: Map<String, WordProgress>, today: LocalDate): String {
        require(words.isNotEmpty()) { "Dictionary is empty" }
        val candidates = (words - recent.toSet()).ifEmpty { words }
        val (seen, new) = candidates.partition { it in progress }
        val forgetting = compareBy<String> { retrievability(progress.getValue(it), today) }
        return seen.filter { !progress.getValue(it).due.isAfter(today) }.minWithOrNull(forgetting)
            ?: new.randomOrNull()
            ?: seen.minWith(forgetting)
    }

    private fun retrievability(p: WordProgress, today: LocalDate): Double =
        Fsrs.retrievability(p.stability, ChronoUnit.DAYS.between(p.lastReview, today))

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
