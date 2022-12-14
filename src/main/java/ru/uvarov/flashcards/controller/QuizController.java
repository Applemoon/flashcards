package ru.uvarov.flashcards.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.uvarov.flashcards.service.QuizService;

@Controller
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/quiz")
    public String quiz(Model model) {
        model.addAttribute("question", quizService.getQuestion());
        return "quiz";
    }

    @GetMapping("/words")
    public String words(Model model) {
        model.addAttribute("allWords", quizService.getAllWords());
        return "words";
    }
}
