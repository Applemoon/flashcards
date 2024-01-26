package ru.uvarov.flashcards.service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FileService {

    @Getter
    private List<String> fileContent;
    @Getter
    private Map<String, String> wordPairs;
    private final String wordFilename;
    private final String errorsFilename;

    public FileService(
        @Value("${quiz.word-filename}") String wordFilename,
        @Value("${quiz.errors-filename}") String errorsFilename
    ) {
        this.wordFilename = wordFilename;
        this.errorsFilename = errorsFilename;
    }

    @PostConstruct
    private void postConstruct() {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream(wordFilename));
        fileContent = new BufferedReader(new InputStreamReader(inputStream)).lines().toList();

        wordPairs = fileContent.stream()
            .parallel()
            .filter(line -> !line.startsWith("#"))
            .filter(line -> !line.isEmpty())
            .peek(line -> {
                if (!line.contains("=")) throw new IllegalArgumentException(line);
            })
            .map(String::trim)
            .map(line -> line.split("="))
            .collect(Collectors.toMap(it -> it[0].trim(), it -> it[1].trim()));

        log.info("Found {} words", wordPairs.size());
    }

    @SneakyThrows
    public void saveWrongWord(String word) {
        FileOutputStream fos = new FileOutputStream(errorsFilename, true);
        fos.write("%s\r\n".formatted(word).getBytes());
        fos.close();
    }
}
