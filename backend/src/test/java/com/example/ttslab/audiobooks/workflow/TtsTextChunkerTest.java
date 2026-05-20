package com.example.ttslab.audiobooks.workflow;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsTextChunkerTest {
    @Test
    void shortTextStaysInOneChunk() {
        List<String> chunks = TtsTextChunker.chunkByUtf8Bytes("Hello world", 3800);

        assertEquals(List.of("Hello world"), chunks);
    }

    @Test
    void emptyOrNullTextReturnsNoChunks() {
        assertTrue(TtsTextChunker.chunkByUtf8Bytes("", 3800).isEmpty());
        assertTrue(TtsTextChunker.chunkByUtf8Bytes(null, 3800).isEmpty());
    }

    @Test
    void longTextSplitsIntoMultipleChunksThatPreserveOriginal() {
        String word = "alpha beta gamma delta ";
        String text = word.repeat(500);

        List<String> chunks = TtsTextChunker.chunkByUtf8Bytes(text, 3800);

        assertTrue(chunks.size() > 1);
        assertEquals(text, String.join("", chunks));
    }

    @Test
    void noChunkExceedsTheByteLimit() {
        String text = "abcdefghij ".repeat(2000);

        List<String> chunks = TtsTextChunker.chunkByUtf8Bytes(text, 100);

        for (String chunk : chunks) {
            assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 100);
        }
        assertEquals(text, String.join("", chunks));
    }

    @Test
    void multiByteCharactersAreMeasuredInBytesAndNeverSplit() {
        // Each emoji is 4 UTF-8 bytes; a limit of 5 bytes forces one emoji per chunk.
        String text = "😀😀😀😀";

        List<String> chunks = TtsTextChunker.chunkByUtf8Bytes(text, 5);

        assertEquals(4, chunks.size());
        for (String chunk : chunks) {
            assertEquals("😀", chunk);
            assertEquals(4, chunk.getBytes(StandardCharsets.UTF_8).length);
        }
        assertEquals(text, String.join("", chunks));
    }

    @Test
    void breaksAtWhitespaceWhenPossible() {
        List<String> chunks = TtsTextChunker.chunkByUtf8Bytes("aaaa bbbb cccc", 6);

        // "aaaa " fits in 6 bytes including the trailing space, so the break keeps words intact.
        assertEquals("aaaa ", chunks.get(0));
        assertEquals("aaaa bbbb cccc", String.join("", chunks));
    }

    @Test
    void singleCharacterLargerThanLimitFails() {
        assertThrows(IllegalArgumentException.class, () -> TtsTextChunker.chunkByUtf8Bytes("😀", 3));
    }

    @Test
    void nonPositiveLimitFails() {
        assertThrows(IllegalArgumentException.class, () -> TtsTextChunker.chunkByUtf8Bytes("text", 0));
    }
}
