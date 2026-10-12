package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.uvarov.flashcards.service.Fsrs.Rating

private const val EPS = 1e-6

class FsrsTest {

    // Эталон посчитан формулами py-fsrs (open-spaced-repetition/py-fsrs, fsrs/scheduler.py)
    // с параметрами по умолчанию на той же последовательности ответов.
    @Test
    fun `first and next - reference sequence - matches py-fsrs`() {
        var memory = Fsrs.first(Rating.GOOD)
        assertMemory(2.306500, 2.118104, 2, memory)

        memory = Fsrs.next(memory, 2, Rating.GOOD)
        assertMemory(10.964332, 2.111214, 11, memory)

        memory = Fsrs.next(memory, 9, Rating.AGAIN)
        assertMemory(1.505217, 7.392238, 2, memory)

        memory = Fsrs.next(memory, 0, Rating.AGAIN)
        assertMemory(0.520224, 9.128075, 1, memory)

        memory = Fsrs.next(memory, 1, Rating.HARD)
        assertMemory(1.061435, 9.406404, 1, memory)

        memory = Fsrs.next(memory, 3, Rating.GOOD)
        assertMemory(2.790081, 9.392226, 3, memory)
    }

    @Test
    fun `retrievability - after stability days - is desired retention`() {
        assertEquals(0.9, Fsrs.retrievability(7.0, 7), EPS)
        assertEquals(1.0, Fsrs.retrievability(7.0, 0), EPS)
    }

    @Test
    fun `next - same elapsed days - hard grows stability less than good`() {
        val start = Fsrs.first(Rating.GOOD)

        val hard = Fsrs.next(start, 3, Rating.HARD)
        val good = Fsrs.next(start, 3, Rating.GOOD)

        assertTrue(hard.stability > start.stability)
        assertTrue(hard.stability < good.stability)
    }

    @Test
    fun `intervalDays - tiny or huge stability - clamped to one day and hundred years`() {
        assertEquals(1, Fsrs.intervalDays(0.01))
        assertEquals(36500, Fsrs.intervalDays(1e9))
    }

    private fun assertMemory(stability: Double, difficulty: Double, interval: Int, memory: Fsrs.Memory) {
        assertEquals(stability, memory.stability, EPS)
        assertEquals(difficulty, memory.difficulty, EPS)
        assertEquals(interval, Fsrs.intervalDays(memory.stability))
    }
}
