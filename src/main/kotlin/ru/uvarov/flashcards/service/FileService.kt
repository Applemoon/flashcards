package ru.uvarov.flashcards.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Service
class FileService(
    @Value("\${quiz.word-filename}") private val wordFilename: String,
    @Value("\${quiz.word-write-filename}") private val wordWriteFilename: String,
) {

    final lateinit var fileContent: List<String>
        private set

    final lateinit var wordPairs: Map<String, String>
        private set

    @PostConstruct
    fun postConstruct() {
        val input = checkNotNull(javaClass.getResourceAsStream(wordFilename)) {
            "Resource not found: $wordFilename"
        }
        fileContent = input.bufferedReader(StandardCharsets.UTF_8).use { it.readLines() }
        wordPairs = parseWordPairs(fileContent)
        log.info("Found {} words", wordPairs.size)
    }

    @Synchronized
    fun deleteWord(wordRu: String) {
        require(wordRu in wordPairs) { "Unknown word: $wordRu" }
        val newLines = fileContent.filterNot { line -> isWordPairLineFor(line, wordRu) }
        Files.write(
            Path.of(wordWriteFilename),
            (newLines.joinToString("\n") + "\n").toByteArray(StandardCharsets.UTF_8),
        )
        fileContent = newLines
        wordPairs = parseWordPairs(newLines)
        log.info("Deleted word '{}', {} words left", wordRu, wordPairs.size)
    }

    companion object {
        private val log = LoggerFactory.getLogger(FileService::class.java)

        fun parseWordPairs(lines: List<String>): Map<String, String> {
            val result = mutableMapOf<String, String>()
            for (raw in lines) {
                if (raw.isEmpty() || raw.startsWith("#")) continue
                require(raw.contains("=")) { raw }
                val parts = raw.trim().split("=", limit = 2)
                val key = parts[0].trim()
                check(key !in result) { "Duplicate Russian key: $key" }
                result[key] = parts[1].trim()
            }
            return result
        }

        private fun isWordPairLineFor(line: String, wordRu: String): Boolean {
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) return false
            return line.trim().split("=", limit = 2)[0].trim() == wordRu
        }
    }
}
