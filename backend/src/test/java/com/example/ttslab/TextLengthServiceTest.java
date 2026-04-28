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
    void countLengthReturnsStringLength() {
        assertEquals(5, textLengthService.countLength("Hallo"));
    }
}
