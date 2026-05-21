package ru.uvarov.flashcards.controller

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import ru.uvarov.flashcards.model.Answer
import ru.uvarov.flashcards.model.Question
import ru.uvarov.flashcards.model.WordPair
import ru.uvarov.flashcards.service.FileService
import ru.uvarov.flashcards.service.QuizService

@WebMvcTest(controllers = [QuizController::class, GlobalExceptionHandler::class])
class QuizControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var quizService: QuizService

    @MockBean
    private lateinit var fileService: FileService

    @Test
    fun `GET quiz - returns quiz view with question in model`() {
        val question = Question("утром", listOf(Answer("ујутру", "утром", correct = true)))
        whenever(quizService.getQuestion()).thenReturn(question)

        mockMvc.perform(get("/quiz"))
            .andExpect(status().isOk)
            .andExpect(view().name("quiz"))
            .andExpect(model().attribute("question", question))
    }

    @Test
    fun `GET words - returns words view with all words and count`() {
        whenever(quizService.getAllWords()).thenReturn(listOf("#Категория", "утром=ујутру"))
        whenever(fileService.wordPairs).thenReturn(mapOf("утром" to "ујутру"))

        mockMvc.perform(get("/words"))
            .andExpect(status().isOk)
            .andExpect(view().name("words"))
            .andExpect(model().attribute("allWords", listOf("#Категория", "утром=ујутру")))
            .andExpect(model().attribute("wordCount", 1))
    }

    @Test
    fun `GET type-srb-ru - exposes srb as word and ru as answer and wordRu`() {
        whenever(quizService.getTypeQuestion()).thenReturn(WordPair("утром", "ујутру"))

        mockMvc.perform(get("/type-srb-ru"))
            .andExpect(status().isOk)
            .andExpect(view().name("type"))
            .andExpect(model().attribute("word", "ујутру"))
            .andExpect(model().attribute("answer", "утром"))
            .andExpect(model().attribute("wordRu", "утром"))
    }

    @Test
    fun `GET type-ru-srb - exposes ru as word answer is srb wordRu is ru`() {
        whenever(quizService.getTypeQuestion()).thenReturn(WordPair("утром", "ујутру"))

        mockMvc.perform(get("/type-ru-srb"))
            .andExpect(status().isOk)
            .andExpect(view().name("type"))
            .andExpect(model().attribute("word", "утром"))
            .andExpect(model().attribute("answer", "ујутру"))
            .andExpect(model().attribute("wordRu", "утром"))
    }

    @Test
    fun `DELETE word - calls service and returns 204`() {
        mockMvc.perform(delete("/word").param("word", "утром"))
            .andExpect(status().isNoContent)

        verify(fileService).deleteWord("утром")
    }

    @Test
    fun `PUT word - calls service with all params and returns 204`() {
        mockMvc.perform(
            put("/word")
                .param("oldRu", "утром")
                .param("newRu", "утречко")
                .param("newSrb", "ујутру")
        ).andExpect(status().isNoContent)

        verify(fileService).updateWord("утром", "утречко", "ујутру")
    }

    @Test
    fun `POST word - calls service and returns 204`() {
        mockMvc.perform(
            post("/word")
                .param("newRu", "новое")
                .param("newSrb", "ново")
        ).andExpect(status().isNoContent)

        verify(fileService).addWord("новое", "ново")
    }

    @Test
    fun `POST answer - correct flag is parsed and forwarded to service`() {
        mockMvc.perform(
            post("/answer")
                .param("word", "утром")
                .param("correct", "true")
        ).andExpect(status().isNoContent)

        verify(fileService).recordAnswer("утром", true)
    }

    @Test
    fun `POST answer - correct false - forwarded as false`() {
        mockMvc.perform(
            post("/answer")
                .param("word", "утром")
                .param("correct", "false")
        ).andExpect(status().isNoContent)

        verify(fileService).recordAnswer("утром", false)
    }

    @Test
    fun `DELETE word - service throws IllegalArgumentException - returns 400 with message`() {
        whenever(fileService.deleteWord(any())).thenThrow(IllegalArgumentException("Unknown word: foo"))

        mockMvc.perform(delete("/word").param("word", "foo"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string("Unknown word: foo"))
    }

    @Test
    fun `POST word - duplicate key from service - returns 400`() {
        whenever(fileService.addWord(eq("утром"), any()))
            .thenThrow(IllegalArgumentException("Duplicate Russian key: утром"))

        mockMvc.perform(
            post("/word").param("newRu", "утром").param("newSrb", "x")
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().string("Duplicate Russian key: утром"))
    }
}
