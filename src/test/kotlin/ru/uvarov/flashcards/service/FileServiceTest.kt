package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class FileServiceTest {

    @Test
    fun `parseWordPairs - valid lines - returns map`() {
        val result = FileService.parseWordPairs(
            listOf(
                "утром=ујутру",
                "вечер=вече",
            )
        )

        assertEquals(mapOf("утром" to "ујутру", "вечер" to "вече"), result)
    }

    @Test
    fun `parseWordPairs - comment and empty lines - are skipped`() {
        val result = FileService.parseWordPairs(
            listOf(
                "#Категория",
                "",
                "утром=ујутру",
            )
        )

        assertEquals(mapOf("утром" to "ујутру"), result)
    }

    @Test
    fun `parseWordPairs - multiple translations - kept as single string`() {
        val result = FileService.parseWordPairs(listOf("неделя=седмица, недеља"))

        assertEquals("седмица, недеља", result["неделя"])
    }

    @Test
    fun `parseWordPairs - whitespace around equals - is trimmed`() {
        val result = FileService.parseWordPairs(listOf("  утром  =  ујутру  "))

        assertEquals(mapOf("утром" to "ујутру"), result)
    }

    @Test
    fun `parseWordPairs - line without equals - throws`() {
        val ex = assertThrows<IllegalArgumentException> {
            FileService.parseWordPairs(listOf("сломанная строка"))
        }
        assertEquals("сломанная строка", ex.message)
    }

    @Test
    fun `parseWordPairs - duplicate russian key - throws`() {
        assertThrows<IllegalStateException> {
            FileService.parseWordPairs(
                listOf(
                    "утром=ујутру",
                    "утром=другой перевод",
                )
            )
        }
    }

    @Test
    fun `postConstruct - loads classpath resource - populates state`() {
        val service = FileService("/test-words.txt", "/tmp/unused-write.txt", "/tmp/unused-errors.txt")

        service.postConstruct()

        val pairs = service.wordPairs
        assertEquals(4, pairs.size)
        assertEquals("ујутру", pairs["утром"])
        assertEquals("седмица, недеља", pairs["неделя"])
        assertTrue(service.fileContent.contains("#Категория один"))
    }

    @Test
    fun `saveWrongWord - appends word - writes utf8 with lf`(@TempDir tmp: Path) {
        val errors = tmp.resolve("errors.txt")
        val service = FileService("/test-words.txt", "/tmp/unused-write.txt", errors.toString())

        service.saveWrongWord("ујутру")
        service.saveWrongWord("ноћ")

        val bytes = Files.readAllBytes(errors)
        assertEquals("ујутру\nноћ\n", String(bytes, StandardCharsets.UTF_8))
    }

    @Test
    fun `deleteWord - existing word - removes from in-memory state`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString(), "/tmp/unused-errors.txt")
        service.postConstruct()

        service.deleteWord("вечер")

        assertFalse("вечер" in service.wordPairs)
        assertFalse(service.fileContent.contains("вечер=вече"))
        assertEquals(3, service.wordPairs.size)
    }

    @Test
    fun `deleteWord - existing word - rewrites file preserving headings and other entries`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString(), "/tmp/unused-errors.txt")
        service.postConstruct()

        service.deleteWord("вечер")

        val written = Files.readString(write, StandardCharsets.UTF_8)
        assertEquals(
            "#Категория один\nутром=ујутру\nночь=ноћ\n\n#Категория два\nнеделя=седмица, недеља\n",
            written,
        )
    }

    @Test
    fun `deleteWord - unknown word - throws and leaves state untouched`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString(), "/tmp/unused-errors.txt")
        service.postConstruct()
        val pairsBefore = service.wordPairs.toMap()

        assertThrows<IllegalArgumentException> {
            service.deleteWord("несуществующее")
        }

        assertEquals(pairsBefore, service.wordPairs)
        assertFalse(Files.exists(write), "file must not be created when delete fails")
    }
}
