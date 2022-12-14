package ru.uvarov.flashcards.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FileService {

    @Getter
    private final List<String> fileContent;

    @Getter
    private final Map<String, String> wordPairs;

    public FileService(@Value("${quiz.filename}") String fileName) {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream(fileName));
        fileContent = new BufferedReader(new InputStreamReader(inputStream))
            .lines()
            .collect(Collectors.toList());

        wordPairs = fileContent.stream()
            .filter(line -> !line.startsWith("#"))
            .filter(line -> !line.isEmpty())
            .peek(line -> {
                if (!line.contains("=")) throw new IllegalArgumentException(line);
            })
            .map(line -> line.trim().split("="))
            .collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim()));

        System.out.println("Найдено " + wordPairs.size() + " слов");
    }
}
