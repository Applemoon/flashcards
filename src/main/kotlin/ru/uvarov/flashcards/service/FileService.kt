package ru.uvarov.flashcards.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ru.uvarov.flashcards.model.DictionaryLine
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Service
class FileService(
    @Value("\${quiz.word-filename}") private val wordFilename: String,
    @Value("\${quiz.word-write-filename}") private val wordWriteFilename: String,
) {

    final lateinit var fileContent: List<String>
        private set

    final lateinit var wordPairs: Map<String, String>
        private set

    final lateinit var wordWeights: Map<String, Int>
        private set

    @PostConstruct
    fun postConstruct() {
        val input = checkNotNull(javaClass.getResourceAsStream(wordFilename)) {
            "Resource not found: $wordFilename"
        }
        fileContent = input.bufferedReader(StandardCharsets.UTF_8).use { it.readLines() }
        val parsed = parseDictionary(fileContent)
        wordPairs = parsed.pairs
        wordWeights = parsed.weights
        log.info("Found {} words", wordPairs.size)
    }

    @Synchronized
    fun deleteWord(wordRu: String) {
        require(wordRu in wordPairs) { "Unknown word: $wordRu" }
        val newLines = fileContent.filterNot { line -> isWordPairLineFor(line, wordRu) }
        persist(newLines)
        log.info("Deleted word '{}', {} words left", wordRu, wordPairs.size)
    }

    @Synchronized
    fun addWord(newRu: String, newSrb: String) {
        val ru = newRu.trim()
        val srb = newSrb.trim()
        validateNewPair(ru, srb)
        require(ru !in wordPairs) { "Duplicate Russian key: $ru" }
        val newLines = fileContent + formatLine(ru, srb, 0)
        persist(newLines)
        log.info("Added '{}={}', {} words total", ru, srb, wordPairs.size)
    }

    @Synchronized
    fun updateWord(oldRu: String, newRu: String, newSrb: String) {
        require(oldRu in wordPairs) { "Unknown word: $oldRu" }
        val ru = newRu.trim()
        val srb = newSrb.trim()
        validateNewPair(ru, srb)
        if (ru != oldRu) {
            require(ru !in wordPairs) { "Duplicate Russian key: $ru" }
        }
        val keptWeight = wordWeights.getValue(oldRu)
        val newLines = fileContent.map { line ->
            if (isWordPairLineFor(line, oldRu)) formatLine(ru, srb, keptWeight) else line
        }
        persist(newLines)
        log.info("Updated '{}' -> '{}={}' (weight={})", oldRu, ru, srb, keptWeight)
    }

    @Synchronized
    fun recordAnswer(wordRu: String, correct: Boolean) {
        require(wordRu in wordPairs) { "Unknown word: $wordRu" }
        val srb = wordPairs.getValue(wordRu)
        val newWeight = wordWeights.getValue(wordRu) + (if (correct) 1 else -1)
        val newLines = fileContent.map { line ->
            if (isWordPairLineFor(line, wordRu)) formatLine(wordRu, srb, newWeight) else line
        }
        persist(newLines)
        log.debug("Recorded {} for '{}', weight now {}", if (correct) "correct" else "wrong", wordRu, newWeight)
    }

    fun getDictionaryLines(): List<DictionaryLine> = parseDictionaryLines(fileContent)

    private fun validateNewPair(ru: String, srb: String) {
        require(ru.isNotEmpty()) { "newRu must not be blank" }
        require(srb.isNotEmpty()) { "newSrb must not be blank" }
        require(!ru.startsWith("#")) { "newRu must not start with '#'" }
        require(!ru.contains("=")) { "newRu must not contain '='" }
    }

    private fun persist(newLines: List<String>) {
        val path = Path.of(wordWriteFilename)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        val bytes = (newLines.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8)
        Files.write(tmp, bytes)
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        val parsed = parseDictionary(newLines)
        fileContent = newLines
        wordPairs = parsed.pairs
        wordWeights = parsed.weights
    }

    data class ParsedDictionary(
        val pairs: Map<String, String>,
        val weights: Map<String, Int>,
    )

    companion object {
        private val log = LoggerFactory.getLogger(FileService::class.java)

        fun parseDictionary(lines: List<String>): ParsedDictionary {
            val pairs = mutableMapOf<String, String>()
            val weights = mutableMapOf<String, Int>()
            for (raw in lines) {
                if (raw.isEmpty() || raw.startsWith("#")) continue
                require(raw.contains("=")) { raw }
                val parts = raw.trim().split("=")
                require(parts.size in 2..3) { "Expected 1 or 2 '=' separators: $raw" }
                val key = parts[0].trim()
                check(key !in pairs) { "Duplicate Russian key: $key" }
                pairs[key] = parts[1].trim()
                weights[key] = if (parts.size == 3) {
                    val w = parts[2].trim().toIntOrNull()
                    require(w != null) { "Invalid weight in line: $raw" }
                    w
                } else 0
            }
            return ParsedDictionary(pairs, weights)
        }

        fun parseWordPairs(lines: List<String>): Map<String, String> =
            parseDictionary(lines).pairs

        fun parseDictionaryLines(lines: List<String>): List<DictionaryLine> =
            lines.map { raw ->
                when {
                    raw.isEmpty() -> DictionaryLine.Blank
                    raw.startsWith("#") -> DictionaryLine.Heading(raw)
                    else -> {
                        val parts = raw.trim().split("=")
                        require(parts.size in 2..3) { "Expected 1 or 2 '=' separators: $raw" }
                        val weight = if (parts.size == 3) {
                            val parsedWeight = parts[2].trim().toIntOrNull()
                            require(parsedWeight != null) { "Invalid weight in line: $raw" }
                            parsedWeight
                        } else 0
                        DictionaryLine.Word(
                            ru = parts[0].trim(),
                            srb = parts[1].trim(),
                            weight = weight,
                        )
                    }
                }
            }

        fun formatLine(ru: String, srb: String, weight: Int): String =
            if (weight == 0) "$ru=$srb" else "$ru=$srb=$weight"

        private fun isWordPairLineFor(line: String, wordRu: String): Boolean {
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) return false
            return line.trim().split("=", limit = 2)[0].trim() == wordRu
        }
    }
}
