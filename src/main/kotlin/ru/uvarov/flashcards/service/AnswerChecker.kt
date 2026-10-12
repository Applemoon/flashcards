package ru.uvarov.flashcards.service

// Проверка ответа, набранного во вкладке «Ввод». Словарь хранит сербскую сторону кириллицей,
// а владелец печатает и латиницей, и кириллицей, поэтому обе стороны сравниваются в латинице:
// кириллица → латиница однозначна (њ → nj, ч → č), обратно — нет (nj бывает и н+ј).
object AnswerChecker {

    enum class Verdict {
        CORRECT,

        // Совпало без учёта диакритики (kornjaca вместо kornjača): засчитывается, буквы подсвечиваются.
        ACCENT,
        WRONG,
    }

    private val cyrillicToLatin = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e",
        'ж' to "ž", 'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj",
        'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'ћ' to "ć", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č",
        'џ' to "dž", 'ш' to "š",
    )

    // đ → dj, а не d: так сербы пишут без диакритики. dž → dz получается само из ž → z.
    private val diacritics = mapOf("č" to "c", "ć" to "c", "š" to "s", "ž" to "z", "đ" to "dj")

    fun toLatin(text: String): String = buildString {
        for (c in text) {
            val latin = cyrillicToLatin[c.lowercaseChar()]
            when {
                latin == null -> append(c)
                c.isUpperCase() -> append(latin.replaceFirstChar { it.uppercaseChar() })
                else -> append(latin)
            }
        }
    }

    // Засчитывается полный перевод или любой из вариантов через запятую или «/» (`седмица, недеља`).
    fun check(input: String, expected: String): Verdict {
        val typed = normalize(input)
        if (typed.isEmpty()) return Verdict.WRONG
        val variants = (listOf(expected) + expected.split(',', '/'))
            .map(::normalize)
            .filter { it.isNotEmpty() }
        return when {
            typed in variants -> Verdict.CORRECT
            fold(typed) in variants.map(::fold) -> Verdict.ACCENT
            else -> Verdict.WRONG
        }
    }

    // Регистр, пунктуация, дефисы и лишние пробелы на проверку не влияют.
    private fun normalize(text: String): String =
        toLatin(text).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun fold(text: String): String =
        diacritics.entries.fold(text) { acc, (from, to) -> acc.replace(from, to) }
}
