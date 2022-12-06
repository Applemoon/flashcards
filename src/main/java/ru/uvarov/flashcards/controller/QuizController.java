package ru.uvarov.flashcards.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class QuizController {

    @GetMapping("/")
    public String quiz(Model model) {
        model.addAttribute("word", "tata");
        model.addAttribute("answer1", "папа");
        model.addAttribute("answer2", "яйцо");
        model.addAttribute("answer3", "шляпа");
        model.addAttribute("answer4", "ретузы");
        return "quiz";
    }
}
