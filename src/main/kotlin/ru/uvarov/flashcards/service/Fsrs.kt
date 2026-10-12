package ru.uvarov.flashcards.service

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// FSRS-6 — планировщик Anki (с 23.10). Формулы и параметры по умолчанию перенесены из эталонной
// py-fsrs (MIT): https://github.com/open-spaced-repetition/py-fsrs/blob/main/fsrs/scheduler.py
// Упрощения против оригинала: нет шагов заучивания в минутах (их роль играет очередь повторов
// QuizService) и нет случайного разброса интервалов (fuzz). Интервалы — целые дни.
object Fsrs {

    enum class Rating(val value: Int) {
        AGAIN(1),
        HARD(2),
        GOOD(3),
        EASY(4),
    }

    // stability — через сколько дней вероятность вспомнить падает до 90%; difficulty — от 1 до 10.
    data class Memory(
        val stability: Double,
        val difficulty: Double,
    )

    private val w = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
        1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
    )
    private const val DESIRED_RETENTION = 0.9
    private const val MAX_INTERVAL_DAYS = 36500
    private const val STABILITY_MIN = 0.001
    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    fun first(rating: Rating): Memory =
        Memory(clampStability(w[rating.value - 1]), clampDifficulty(initialDifficulty(rating)))

    // elapsedDays — целых дней с прошлого ответа; 0 — тот же день, для него своя формула.
    fun next(memory: Memory, elapsedDays: Long, rating: Rating): Memory {
        val stability = if (elapsedDays < 1) {
            shortTermStability(memory.stability, rating)
        } else {
            val retrievability = retrievability(memory.stability, elapsedDays)
            if (rating == Rating.AGAIN) {
                forgetStability(memory, retrievability)
            } else {
                recallStability(memory, retrievability, rating)
            }
        }
        return Memory(clampStability(stability), nextDifficulty(memory.difficulty, rating))
    }

    // Вероятность вспомнить слово через elapsedDays дней.
    fun retrievability(stability: Double, elapsedDays: Long): Double =
        (1 + factor * elapsedDays.coerceAtLeast(0) / stability).pow(decay)

    fun intervalDays(stability: Double): Int =
        (stability / factor * (DESIRED_RETENTION.pow(1 / decay) - 1))
            .roundToInt()
            .coerceIn(1, MAX_INTERVAL_DAYS)

    private fun initialDifficulty(rating: Rating): Double = w[4] - exp(w[5] * (rating.value - 1)) + 1

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * stability.pow(-w[19])
        if (rating != Rating.AGAIN) increase = increase.coerceAtLeast(1.0)
        return stability * increase
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -w[6] * (rating.value - 3)
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        // Возврат к сложности «легко»: без него сложность только копится от ошибок.
        return clampDifficulty(w[7] * initialDifficulty(Rating.EASY) + (1 - w[7]) * damped)
    }

    private fun forgetStability(memory: Memory, retrievability: Double): Double {
        val longTerm = w[11] * memory.difficulty.pow(-w[12]) * ((memory.stability + 1).pow(w[13]) - 1) *
            exp((1 - retrievability) * w[14])
        val shortTerm = memory.stability / exp(w[17] * w[18])
        return min(longTerm, shortTerm)
    }

    private fun recallStability(memory: Memory, retrievability: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.HARD) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) w[16] else 1.0
        return memory.stability * (
            1 + exp(w[8]) * (11 - memory.difficulty) * memory.stability.pow(-w[9]) *
                (exp((1 - retrievability) * w[10]) - 1) * hardPenalty * easyBonus
            )
    }

    private fun clampStability(stability: Double): Double = stability.coerceAtLeast(STABILITY_MIN)

    private fun clampDifficulty(difficulty: Double): Double = difficulty.coerceIn(1.0, 10.0)
}
