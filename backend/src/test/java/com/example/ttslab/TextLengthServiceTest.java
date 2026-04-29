package com.example.ttslab;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextLengthServiceTest {

    private final TextLengthService textLengthService = new TextLengthService();

    @Test
    void countLengthReturnsZeroForNull() {
        assertEquals(0, textLengthService.countLength(null));
    }

    @Test
    void countLengthReturnsZeroForEmptyText() {
        assertEquals(0, textLengthService.countLength(""));
    }

    @Test
    void countLengthReturnsStringLength() {
        assertEquals(5, textLengthService.countLength("Hallo"));
    }

    @Test
    void countLengthSupportsUnicodeText() {
        assertEquals(5, textLengthService.countLength("Grüße"));
    }

    @Test
    void countLengthSupportsLargeInput() {
        String largeInput = "a".repeat(10_000);
        assertEquals(10_000, textLengthService.countLength(largeInput));
    }
}
