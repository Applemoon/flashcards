package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.uvarov.flashcards.model.WordProgress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 10, 12)

class ProgressServiceTest {

    @Test
    fun `postConstruct - no file yet - empty progress`(@TempDir tmp: Path) {
        val service = ProgressService(tmp.resolve("progress.txt").toString())
        service.postConstruct()

        assertTrue(service.progress.isEmpty())
    }

    @Test
    fun `record - new word - first memory and due date written to file`(@TempDir tmp: Path) {
        val file = tmp.resolve("progress.txt")
        val service = ProgressService(file.toString())
        service.postConstruct()

        service.record("черепаха", Fsrs.Rating.GOOD, TODAY)

        val p = service.progress.getValue("черепаха")
        assertEquals(TODAY, p.lastReview)
        assertEquals(TODAY.plusDays(2), p.due)
        assertEquals(
            "черепаха=2.3065=2.1181=2026-10-12=2026-10-14\n",
            Files.readString(file, StandardCharsets.UTF_8),
        )
    }

    @Test
    fun `record - known word days later - uses elapsed days`(@TempDir tmp: Path) {
        val service = ProgressService(tmp.resolve("progress.txt").toString())
        service.postConstruct()
        service.record("черепаха", Fsrs.Rating.GOOD, TODAY)

        service.record("черепаха", Fsrs.Rating.GOOD, TODAY.plusDays(2))

        val p = service.progress.getValue("черепаха")
        assertEquals(10.964332, p.stability, 1e-6)
        assertEquals(TODAY.plusDays(13), p.due)
    }

    @Test
    fun `postConstruct - written file - read back`(@TempDir tmp: Path) {
        val file = tmp.resolve("progress.txt")
        val writer = ProgressService(file.toString())
        writer.postConstruct()
        writer.record("черепаха", Fsrs.Rating.GOOD, TODAY)
        writer.record("встреча, совещание", Fsrs.Rating.AGAIN, TODAY)

        val reader = ProgressService(file.toString())
        reader.postConstruct()

        assertEquals(listOf("черепаха", "встреча, совещание"), reader.progress.keys.toList())
        assertEquals(WordProgress(2.3065, 2.1181, TODAY, TODAY.plusDays(2)), reader.progress["черепаха"])
    }

    @Test
    fun `rename - known word - keeps progress and position under new key`(@TempDir tmp: Path) {
        val service = ProgressService(tmp.resolve("progress.txt").toString())
        service.postConstruct()
        service.record("утром", Fsrs.Rating.GOOD, TODAY)
        service.record("вечер", Fsrs.Rating.HARD, TODAY)
        val kept = service.progress.getValue("утром")

        service.rename("утром", "утречком")

        assertEquals(listOf("утречком", "вечер"), service.progress.keys.toList())
        assertEquals(kept, service.progress["утречком"])
    }

    @Test
    fun `remove - known word - dropped from progress and file`(@TempDir tmp: Path) {
        val file = tmp.resolve("progress.txt")
        val service = ProgressService(file.toString())
        service.postConstruct()
        service.record("утром", Fsrs.Rating.GOOD, TODAY)

        service.remove("утром")

        assertFalse("утром" in service.progress)
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("утром"))
    }
}
