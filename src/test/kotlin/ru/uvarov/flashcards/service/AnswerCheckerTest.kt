package ru.uvarov.flashcards.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.uvarov.flashcards.service.AnswerChecker.Verdict

class AnswerCheckerTest {

    @Test
    fun `toLatin - cyrillic with digraph letters - transliterated keeping case`() {
        assertEquals("Ljubav, njiva, džep, đak", AnswerChecker.toLatin("Љубав, њива, џеп, ђак"))
    }

    @Test
    fun `toLatin - latin and digits - left as is`() {
        assertEquals("3D štampač, SSD disk", AnswerChecker.toLatin("3Д штампач, SSD диск"))
    }

    @Test
    fun `check - cyrillic input equals answer - correct`() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("корњача", "корњача"))
    }

    @Test
    fun `check - latin input with diacritics - correct`() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("kornjača", "корњача"))
    }

    @Test
    fun `check - latin input without diacritics - accent`() {
        assertEquals(Verdict.ACCENT, AnswerChecker.check("kornjaca", "корњача"))
    }

    @Test
    fun `check - dj for đ and dz for dž - accent`() {
        assertEquals(Verdict.ACCENT, AnswerChecker.check("djak", "ђак"))
        assertEquals(Verdict.ACCENT, AnswerChecker.check("dzep", "џеп"))
    }

    @Test
    fun `check - case, punctuation and extra spaces differ - correct`() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("  Dobro   JUTRO! ", "добро јутро"))
    }

    @Test
    fun `check - one of comma variants - correct`() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("nedelja", "седмица, недеља"))
        assertEquals(Verdict.CORRECT, AnswerChecker.check("sedmica, nedelja", "седмица, недеља"))
    }

    @Test
    fun `check - one of slash variants - correct`() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("link", "веза / линк"))
    }

    @Test
    fun `check - blank input - wrong`() {
        assertEquals(Verdict.WRONG, AnswerChecker.check("   ", "корњача"))
    }

    @Test
    fun `check - different word - wrong`() {
        assertEquals(Verdict.WRONG, AnswerChecker.check("kengur", "корњача"))
    }

    @Test
    fun `check - part of a variant - wrong`() {
        assertEquals(Verdict.WRONG, AnswerChecker.check("sed", "седмица, недеља"))
    }
}
