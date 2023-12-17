package ru.uvarov.flashcards.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Getter
@Service
public class FileService {

    private final List<String> fileContent;

    private final Map<String, String> wordPairs;

    public FileService(@Value("${quiz.filename}") String fileName) {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream(fileName));
        fileContent = new BufferedReader(new InputStreamReader(inputStream)).lines().toList();

        wordPairs = fileContent.stream()
            .filter(line -> !line.startsWith("#"))
            .filter(line -> !line.isEmpty())
            .peek(line -> {
                if (!line.contains("=")) throw new IllegalArgumentException(line);
            })
            .map(String::trim)
            .map(line -> line.split("="))
            .collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim()));

        System.out.printf("Найдено %s слов%n", wordPairs.size());
    }
}
