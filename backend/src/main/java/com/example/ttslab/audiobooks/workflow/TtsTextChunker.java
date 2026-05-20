package com.example.ttslab.audiobooks.workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into chunks that each stay within a maximum UTF-8 byte size.
 * Google Cloud Text-to-Speech rejects a single request whose input is larger
 * than 4000 bytes, so longer passages must be broken into provider-safe parts.
 */
public final class TtsTextChunker {
    private TtsTextChunker() {
    }

    public static List<String> chunkByUtf8Bytes(String text, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        int start = 0;
        int length = text.length();
        while (start < length) {
            int hardEnd = byteLimitedEnd(text, start, maxBytes);
            int end = hardEnd < length ? preferWhitespaceBreak(text, start, hardEnd) : hardEnd;
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    // Largest exclusive end index so that text[start, end) fits in maxBytes; always greater than start.
    private static int byteLimitedEnd(String text, int start, int maxBytes) {
        int bytes = 0;
        int i = start;
        int length = text.length();
        while (i < length) {
            int codePoint = text.codePointAt(i);
            int unitBytes = utf8Length(codePoint);
            if (unitBytes > maxBytes) {
                throw new IllegalArgumentException("A single character is larger than the maximum byte limit of " + maxBytes);
            }
            if (bytes + unitBytes > maxBytes) {
                break;
            }
            bytes += unitBytes;
            i += Character.charCount(codePoint);
        }
        return i;
    }

    // Break at the last whitespace within the chunk so words are not split mid-token when avoidable.
    private static int preferWhitespaceBreak(String text, int start, int hardEnd) {
        for (int i = hardEnd - 1; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i + 1;
            }
        }
        return hardEnd;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }
}
