package ru.uvarov.flashcards.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import ru.uvarov.flashcards.model.Question;
import ru.uvarov.flashcards.service.QuizService;

@Controller
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/")
    public String quiz(Model model) {
        Question question = quizService.getQuestion();
        model.addAttribute("word", question.getWord());
        model.addAttribute("answer1", question.getAnswersList().get(0).getWord());
        model.addAttribute("answer2", question.getAnswersList().get(1).getWord());
        model.addAttribute("answer3", question.getAnswersList().get(2).getWord());
        model.addAttribute("answer4", question.getAnswersList().get(3).getWord());
        return "quiz";
    }
}
