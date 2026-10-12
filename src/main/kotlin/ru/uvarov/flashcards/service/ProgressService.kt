package ru.uvarov.flashcards.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.uvarov.flashcards.model.WordProgress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

// Расписание показов живёт отдельно от словаря, в файле строк `русское=stability=difficulty=последний=срок`:
// словарь остаётся чистым, а «=» в русском ключе запрещён валидацией FileService.
// Файл читается с того же пути, куда пишется: это состояние, а не ресурс сборки.
@Service
class ProgressService(
    @Value("\${quiz.progress-filename}") private val progressFilename: String,
) {

    final lateinit var progress: Map<String, WordProgress>
        private set

    @PostConstruct
    fun postConstruct() {
        val path = Path.of(progressFilename)
        progress = if (Files.exists(path)) {
            parseProgress(Files.readAllLines(path, StandardCharsets.UTF_8))
        } else {
            emptyMap()
        }
        log.info("Found progress for {} words", progress.size)
    }

    @Synchronized
    fun record(wordRu: String, rating: Fsrs.Rating, today: LocalDate) {
        val old = progress[wordRu]
        val memory = if (old == null) {
            Fsrs.first(rating)
        } else {
            Fsrs.next(Fsrs.Memory(old.stability, old.difficulty), ChronoUnit.DAYS.between(old.lastReview, today), rating)
        }
        val due = today.plusDays(Fsrs.intervalDays(memory.stability).toLong())
        persist(progress + (wordRu to WordProgress(memory.stability, memory.difficulty, today, due)))
        log.debug("Recorded {} for '{}', due {}", rating, wordRu, due)
    }

    @Synchronized
    fun rename(oldRu: String, newRu: String) {
        if (oldRu !in progress) return
        persist(progress.mapKeys { (ru, _) -> if (ru == oldRu) newRu else ru })
    }

    @Synchronized
    fun remove(wordRu: String) {
        if (wordRu !in progress) return
        persist(progress - wordRu)
    }

    private fun persist(newProgress: Map<String, WordProgress>) {
        val path = Path.of(progressFilename)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        val lines = newProgress.map { (ru, p) -> formatLine(ru, p) }
        val bytes = (lines.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8)
        Files.write(tmp, bytes)
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        progress = newProgress
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProgressService::class.java)

        fun parseProgress(lines: List<String>): Map<String, WordProgress> {
            val result = linkedMapOf<String, WordProgress>()
            for (line in lines) {
                if (line.isBlank()) continue
                val parts = line.split("=")
                require(parts.size == 5) { "Expected 5 '='-separated fields: $line" }
                result[parts[0]] = WordProgress(
                    stability = parts[1].toDouble(),
                    difficulty = parts[2].toDouble(),
                    lastReview = LocalDate.parse(parts[3]),
                    due = LocalDate.parse(parts[4]),
                )
            }
            return result
        }

        // 4 знака после запятой: полная точность double только зашумит диффы в git.
        fun formatLine(ru: String, p: WordProgress): String =
            String.format(Locale.ROOT, "%s=%.4f=%.4f=%s=%s", ru, p.stability, p.difficulty, p.lastReview, p.due)
    }
}
