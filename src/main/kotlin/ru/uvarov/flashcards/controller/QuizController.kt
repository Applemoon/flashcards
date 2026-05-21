package ru.uvarov.flashcards.controller

import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
        model.addAttribute("wordCount", fileService.wordPairs.size)
        return "words"
    }

    @GetMapping("/type-srb-ru")
    fun typeSrbRu(model: Model): String {
        val pair = quizService.getTypeQuestion()
        model.addAttribute("word", pair.wordSrb)
        model.addAttribute("answer", pair.wordRu)
        model.addAttribute("wordRu", pair.wordRu)
        return "type"
    }

    @GetMapping("/type-ru-srb")
    fun typeRuSrb(model: Model): String {
        val pair = quizService.getTypeQuestion()
        model.addAttribute("word", pair.wordRu)
        model.addAttribute("answer", pair.wordSrb)
        model.addAttribute("wordRu", pair.wordRu)
        return "type"
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
    ): ResponseEntity<Void> {
        fileService.recordAnswer(word, correct)
        return ResponseEntity.noContent().build()
    }
}
