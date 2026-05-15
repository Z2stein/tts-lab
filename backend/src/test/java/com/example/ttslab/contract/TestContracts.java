package com.example.ttslab.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class TestContracts {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private TestContracts() {
    }

    public static String readText(String relativePath) throws IOException {
        return Files.readString(resolve(relativePath), StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(String relativePath) throws IOException {
        return Files.readAllBytes(resolve(relativePath));
    }

    public static <T> T readJson(String relativePath, Class<T> type) throws IOException {
        return OBJECT_MAPPER.readValue(readText(relativePath), type);
    }

    private static Path resolve(String relativePath) {
        Path[] candidates = new Path[] {
            Paths.get("..", "test-contracts", relativePath),
            Paths.get("test-contracts", relativePath)
        };

        for (Path candidate : candidates) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (Files.exists(absolute)) {
                return absolute;
            }
        }

        throw new IllegalStateException("Unable to locate test contract file: " + relativePath);
    }
}
