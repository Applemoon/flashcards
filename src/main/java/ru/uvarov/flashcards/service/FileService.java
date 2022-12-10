package ru.uvarov.flashcards.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FileService {

    @Value("${quiz.filename}")
    private String fileName;

    public Map<String, String> readPairs() {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream(fileName));
        return new BufferedReader(new InputStreamReader(inputStream))
            .lines()
            .filter(line -> !line.startsWith("#"))
            .filter(line -> !line.isEmpty())
            .peek(line -> {
                if (!line.contains("=")) throw new IllegalArgumentException(line);
            })
            .map(line -> line.trim().split("="))
            .collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim()));
    }
}
