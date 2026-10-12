package ru.uvarov.flashcards.controller

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import ru.uvarov.flashcards.model.Question
import ru.uvarov.flashcards.service.AnswerChecker
import ru.uvarov.flashcards.service.FileService
import ru.uvarov.flashcards.service.QuizService

@Controller
class QuizController(
    private val quizService: QuizService,
    private val fileService: FileService,
) {

    @GetMapping("/")
    fun root(model: Model): String = trainer("quiz", model)

    // Все вкладки-тренажёры — одна страница quiz.html: режим решает, как показать вопрос.
    @GetMapping("/{mode:quiz|type|cards|mix}")
    fun trainer(@PathVariable mode: String, model: Model): String {
        model.addAttribute("question", quizService.getQuestion())
        model.addAttribute("mode", mode)
        return "quiz"
    }

    // Следующий вопрос для quiz.html без перезагрузки страницы.
    @GetMapping("/question")
    @ResponseBody
    fun question(): Question = quizService.getQuestion()

    @GetMapping("/words")
    fun words(model: Model): String {
        model.addAttribute("dictionaryLines", quizService.getDictionaryLines())
        model.addAttribute("wordCount", fileService.wordPairs.size)
        return "words"
    }

    @DeleteMapping("/word")
    fun deleteWord(@RequestParam word: String): ResponseEntity<Void> {
        fileService.deleteWord(word)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/word")
    fun updateWord(
        @RequestParam oldRu: String,
        @RequestParam newRu: String,
        @RequestParam newSrb: String,
    ): ResponseEntity<Void> {
        fileService.updateWord(oldRu, newRu, newSrb)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/word")
    fun addWord(
        @RequestParam newRu: String,
        @RequestParam newSrb: String,
    ): ResponseEntity<Void> {
        fileService.addWord(newRu, newSrb)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/answer")
    fun recordAnswer(
        @RequestParam word: String,
        @RequestParam correct: Boolean,
        @RequestParam(defaultValue = "false") recall: Boolean,
    ): ResponseEntity<Void> {
        quizService.recordAnswer(word, correct, recall)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/typed")
    @ResponseBody
    fun checkTyped(
        @RequestParam word: String,
        @RequestParam(defaultValue = "") input: String,
    ): AnswerChecker.Verdict = quizService.checkTyped(word, input)
}
