package ru.uvarov.flashcards.service;

import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class FileService {

    private static final String FILE_NAME = "./questions.txt";

    @SneakyThrows
    public Map<String, String> readPairs() {
        Map<String, String> pairs = new HashMap<>();
        URL url = getClass().getClassLoader().getResource(FILE_NAME);
        File file = new File(Objects.requireNonNull(url).toURI());
        Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)
            .forEach((line) -> {
                if (!line.startsWith("#") && !line.isEmpty()) {
                    String[] pair = line.trim().split("=");
                    pairs.put(pair[0].trim(), pair[1].trim());
                }
            });

        return pairs;
    }
}
