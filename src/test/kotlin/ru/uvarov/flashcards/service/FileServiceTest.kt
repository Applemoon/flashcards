package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import ru.uvarov.flashcards.model.DictionaryLine
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
        val service = FileService("/test-words.txt", "/tmp/unused-write.txt")

        service.postConstruct()

        val pairs = service.wordPairs
        assertEquals(4, pairs.size)
        assertEquals("ујутру", pairs["утром"])
        assertEquals("седмица, недеља", pairs["неделя"])
        assertTrue(service.fileContent.contains("#Категория один"))
    }

    @Test
    fun `deleteWord - existing word - removes from in-memory state`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.deleteWord("вечер")

        assertFalse("вечер" in service.wordPairs)
        assertFalse(service.fileContent.contains("вечер=вече"))
        assertEquals(3, service.wordPairs.size)
    }

    @Test
    fun `deleteWord - existing word - rewrites file preserving headings and other entries`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
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
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()
        val pairsBefore = service.wordPairs.toMap()

        assertThrows<IllegalArgumentException> {
            service.deleteWord("несуществующее")
        }

        assertEquals(pairsBefore, service.wordPairs)
        assertFalse(Files.exists(write), "file must not be created when delete fails")
    }

    @Test
    fun `updateWord - same key new value - replaces translation`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.updateWord("вечер", "вечер", "новый перевод")

        assertEquals("новый перевод", service.wordPairs["вечер"])
        assertTrue(service.fileContent.contains("вечер=новый перевод"))
        val written = Files.readString(write, StandardCharsets.UTF_8)
        assertTrue(written.contains("вечер=новый перевод"))
        assertFalse(written.contains("вечер=вече"))
    }

    @Test
    fun `updateWord - new russian key - renames entry preserving order`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.updateWord("вечер", "вечерок", "вече")

        assertFalse("вечер" in service.wordPairs)
        assertEquals("вече", service.wordPairs["вечерок"])
        assertEquals(
            "#Категория один\nутром=ујутру\nвечерок=вече\nночь=ноћ\n\n#Категория два\nнеделя=седмица, недеља\n",
            Files.readString(write, StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `updateWord - new ru collides with existing key - throws`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        assertThrows<IllegalArgumentException> {
            service.updateWord("вечер", "утром", "что-то")
        }

        assertFalse(Files.exists(write))
        assertEquals("вече", service.wordPairs["вечер"])
        assertEquals("ујутру", service.wordPairs["утром"])
    }

    @Test
    fun `updateWord - unknown old key - throws`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        assertThrows<IllegalArgumentException> {
            service.updateWord("неизвестное", "новое", "translation")
        }

        assertFalse(Files.exists(write))
    }

    @Test
    fun `updateWord - blank ru or srb - throws`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        assertThrows<IllegalArgumentException> { service.updateWord("вечер", "  ", "вече") }
        assertThrows<IllegalArgumentException> { service.updateWord("вечер", "вечер", "  ") }

        assertEquals("вече", service.wordPairs["вечер"])
    }

    @Test
    fun `updateWord - new ru contains equals or starts with hash - throws`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        assertThrows<IllegalArgumentException> { service.updateWord("вечер", "a=b", "вече") }
        assertThrows<IllegalArgumentException> { service.updateWord("вечер", "#header", "вече") }
    }

    @Test
    fun `addWord - new pair - appends to end of file`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.addWord("новое", "ново")

        assertEquals("ново", service.wordPairs["новое"])
        assertEquals(5, service.wordPairs.size)
        assertEquals(
            "#Категория один\nутром=ујутру\nвечер=вече\nночь=ноћ\n\n#Категория два\nнеделя=седмица, недеља\nновое=ново\n",
            Files.readString(write, StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `addWord - whitespace around values - trimmed before saving`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.addWord("  новое  ", "  ново  ")

        assertEquals("ново", service.wordPairs["новое"])
        assertTrue(service.fileContent.last() == "новое=ново")
    }

    @Test
    fun `addWord - duplicate russian key - throws and leaves state untouched`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()
        val pairsBefore = service.wordPairs.toMap()

        assertThrows<IllegalArgumentException> {
            service.addWord("утром", "другой")
        }

        assertEquals(pairsBefore, service.wordPairs)
        assertFalse(Files.exists(write))
    }

    @Test
    fun `addWord - blank ru or srb - throws`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        assertThrows<IllegalArgumentException> { service.addWord("   ", "ново") }
        assertThrows<IllegalArgumentException> { service.addWord("новое", "   ") }
    }

    @Test
    fun `addWord - ru contains equals or starts with hash - throws`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        assertThrows<IllegalArgumentException> { service.addWord("a=b", "ново") }
        assertThrows<IllegalArgumentException> { service.addWord("#header", "ново") }
    }

    @Test
    fun `parseDictionary - line with weight - parses weight value`() {
        val parsed = FileService.parseDictionary(
            listOf("утром=ујутру=3", "вечер=вече=-2", "ночь=ноћ")
        )

        assertEquals(mapOf("утром" to "ујутру", "вечер" to "вече", "ночь" to "ноћ"), parsed.pairs)
        assertEquals(mapOf("утром" to 3, "вечер" to -2, "ночь" to 0), parsed.weights)
    }

    @Test
    fun `parseDictionary - words under headings - assigns category from preceding heading`() {
        val parsed = FileService.parseDictionary(
            listOf(
                "#Категория один",
                "утром=ујутру",
                "вечер=вече",
                "",
                "#Категория два",
                "неделя=седмица, недеља",
            )
        )

        assertEquals(
            mapOf(
                "утром" to "Категория один",
                "вечер" to "Категория один",
                "неделя" to "Категория два",
            ),
            parsed.categories,
        )
    }

    @Test
    fun `parseDictionary - word before any heading - gets empty category`() {
        val parsed = FileService.parseDictionary(listOf("сирота=сироче"))

        assertEquals(mapOf("сирота" to ""), parsed.categories)
    }

    @Test
    fun `postConstruct - loads classpath resource - populates categories`() {
        val service = FileService("/test-words.txt", "/tmp/unused-write.txt")

        service.postConstruct()

        assertEquals("Категория один", service.wordCategories["утром"])
        assertEquals("Категория два", service.wordCategories["неделя"])
    }

    @Test
    fun `parseDictionary - invalid weight format - throws`() {
        assertThrows<IllegalArgumentException> {
            FileService.parseDictionary(listOf("утром=ујутру=абв"))
        }
    }

    @Test
    fun `parseDictionary - more than three equals - throws`() {
        assertThrows<IllegalArgumentException> {
            FileService.parseDictionary(listOf("а=б=1=2"))
        }
    }

    @Test
    fun `parseDictionaryLines - weighted word - separates translation and weight`() {
        val lines = FileService.parseDictionaryLines(
            listOf(
                "#Категория",
                "",
                "неделя=седмица, недеља=3",
            )
        )

        assertEquals(
            listOf(
                DictionaryLine.Heading("#Категория"),
                DictionaryLine.Blank,
                DictionaryLine.Word("неделя", "седмица, недеља", 3),
            ),
            lines,
        )
    }

    @Test
    fun `recordAnswer - correct - increments weight and persists`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.recordAnswer("вечер", correct = true)

        assertEquals(1, service.wordWeights["вечер"])
        val written = Files.readString(write, StandardCharsets.UTF_8)
        assertTrue(written.contains("вечер=вече=1"))
    }

    @Test
    fun `recordAnswer - wrong - decrements weight`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.recordAnswer("вечер", correct = false)
        service.recordAnswer("вечер", correct = false)

        assertEquals(-2, service.wordWeights["вечер"])
        assertTrue(Files.readString(write, StandardCharsets.UTF_8).contains("вечер=вече=-2"))
    }

    @Test
    fun `recordAnswer - weight returns to zero - line drops weight suffix`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        service.recordAnswer("вечер", correct = true)
        service.recordAnswer("вечер", correct = false)

        assertEquals(0, service.wordWeights["вечер"])
        val written = Files.readString(write, StandardCharsets.UTF_8)
        assertTrue(written.contains("вечер=вече\n"))
        assertTrue(!written.contains("вечер=вече=0"))
    }

    @Test
    fun `updateWord - existing word with weight - preserves weight in new line`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()
        service.recordAnswer("вечер", correct = true)
        service.recordAnswer("вечер", correct = true)

        service.updateWord("вечер", "вечерок", "вече")

        assertEquals(2, service.wordWeights["вечерок"])
        assertTrue(Files.readString(write, StandardCharsets.UTF_8).contains("вечерок=вече=2"))
    }

    @Test
    fun `recordAnswer - unknown word - throws`(@TempDir tmp: Path) {
        val write = tmp.resolve("questions.txt")
        val service = FileService("/test-words.txt", write.toString())
        service.postConstruct()

        assertThrows<IllegalArgumentException> {
            service.recordAnswer("несуществующее", correct = true)
        }
    }
}
