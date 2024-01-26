package ru.uvarov.flashcards;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class FlashcardsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashcardsApplication.class, args);
        try {
            new ProcessBuilder("open", "http://localhost:8080").start();
        } catch (IOException e) {
            log.error("Failed to run browser", e);
        }
    }
}
