package ru.uvarov.flashcards.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

@Service
class FileService(
    @Value("\${quiz.word-filename}") private val wordFilename: String,
    @Value("\${quiz.errors-filename}") private val errorsFilename: String,
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

    fun saveWrongWord(word: String) {
        FileOutputStream(errorsFilename, true).use { fos ->
            fos.write("$word\n".toByteArray(StandardCharsets.UTF_8))
        }
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
    }
}
