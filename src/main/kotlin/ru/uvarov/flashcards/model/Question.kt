package ru.uvarov.flashcards.model

data class Question(
    val word: String,
    val answersList: List<Answer>,
    val retry: Boolean = false,
    // Предложение (4+ слов или с «.»/«?» в конце): во «Вводе» его не печатают, а оценивают сами.
    val sentence: Boolean = false,
    // Верный перевод латиницей — «Ввод» и «Карточки» показывают его рядом с кириллицей.
    val answerLatin: String = "",
    // Слов, которым по расписанию пора на повторение (срок сегодня или раньше).
    val dueCount: Int = 0,
)
