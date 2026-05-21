package ru.uvarov.flashcards.controller

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import ru.uvarov.flashcards.service.FileService
import ru.uvarov.flashcards.service.QuizService

@Controller
class QuizController(
    private val quizService: QuizService,
    private val fileService: FileService,
) {

    @GetMapping("/quiz")
    fun quiz(model: Model): String {
        model.addAttribute("question", quizService.getQuestion())
        return "quiz"
    }

    @GetMapping("/words")
    fun words(model: Model): String {
        model.addAttribute("allWords", quizService.getAllWords())
        return "words"
    }

    @GetMapping("/type-srb-ru")
    fun typeSrbRu(model: Model): String {
        val pair = quizService.getTypeQuestion()
        model.addAttribute("word", pair.wordSrb)
        model.addAttribute("answer", pair.wordRu)
        return "type"
    }

    @GetMapping("/type-ru-srb")
    fun typeRuSrb(model: Model): String {
        val pair = quizService.getTypeQuestion()
        model.addAttribute("word", pair.wordRu)
        model.addAttribute("answer", pair.wordSrb)
        return "type"
    }

    @GetMapping("/error")
    fun addError(@RequestParam word: String): ResponseEntity<Void> {
        fileService.saveWrongWord(word)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/word")
    fun deleteWord(@RequestParam word: String): ResponseEntity<Void> {
        fileService.deleteWord(word)
        return ResponseEntity.noContent().build()
    }
}
