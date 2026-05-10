package com.example.ttslab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestUsageMeasurer")
class RequestUsageMeasurerTest {
    private final RequestUsageMeasurer measurer = new RequestUsageMeasurer();

    // ============ measure() null/blank input tests ============

    @Test
    @DisplayName("measure returns 0 for null input")
    void measureReturnsZeroForNullInput() {
        assertThat(measurer.measure(null, RequestRateLimitUnit.WORDS)).isEqualTo(0L);
        assertThat(measurer.measure(null, RequestRateLimitUnit.TOKENS)).isEqualTo(0L);
    }

    @Test
    @DisplayName("measure returns 0 for blank input")
    void measureReturnsZeroForBlankInput() {
        assertThat(measurer.measure("", RequestRateLimitUnit.WORDS)).isEqualTo(0L);
        assertThat(measurer.measure("   ", RequestRateLimitUnit.WORDS)).isEqualTo(0L);
        assertThat(measurer.measure("\t\n", RequestRateLimitUnit.WORDS)).isEqualTo(0L);
    }

    @Test
    @DisplayName("measure returns 0 for blank input with TOKENS unit")
    void measureReturnsZeroForBlankInputTokens() {
        assertThat(measurer.measure("", RequestRateLimitUnit.TOKENS)).isEqualTo(0L);
        assertThat(measurer.measure("   ", RequestRateLimitUnit.TOKENS)).isEqualTo(0L);
    }

    // ============ measure() WORDS unit tests ============

    @Test
    @DisplayName("measure counts single word")
    void measureCountsSingleWord() {
        assertThat(measurer.measure("hello", RequestRateLimitUnit.WORDS)).isEqualTo(1L);
    }

    @Test
    @DisplayName("measure counts multiple words")
    void measureCountsMultipleWords() {
        assertThat(measurer.measure("hello world test", RequestRateLimitUnit.WORDS)).isEqualTo(3L);
    }

    @Test
    @DisplayName("measure handles extra whitespace between words")
    void measureHandlesExtraWhitespaceBetweenWords() {
        assertThat(measurer.measure("hello    world", RequestRateLimitUnit.WORDS)).isEqualTo(2L);
        assertThat(measurer.measure("hello\t\nworld", RequestRateLimitUnit.WORDS)).isEqualTo(2L);
    }

    @Test
    @DisplayName("measure handles leading/trailing whitespace")
    void measureHandlesLeadingTrailingWhitespace() {
        assertThat(measurer.measure("  hello world  ", RequestRateLimitUnit.WORDS)).isEqualTo(2L);
        assertThat(measurer.measure("\thello\n", RequestRateLimitUnit.WORDS)).isEqualTo(1L);
    }

    @Test
    @DisplayName("measure counts words in sentence with punctuation")
    void measureCountsWordsWithPunctuation() {
        // "Hello, world! How are you?" -> splits on whitespace: Hello, | world! | How | are | you? = 5 words
        assertThat(measurer.measure("Hello, world! How are you?", RequestRateLimitUnit.WORDS)).isEqualTo(5L);
    }

    @Test
    @DisplayName("measure counts words with contractions")
    void measureCountsWordsWithContractions() {
        assertThat(measurer.measure("don't can't won't", RequestRateLimitUnit.WORDS)).isEqualTo(3L);
    }

    // ============ measure() TOKENS unit tests ============

    @Test
    @DisplayName("measure estimates tokens from text length")
    void measureEstimatesTokensFromTextLength() {
        // 4 characters = 1 token (ceiling of 4/4.0)
        assertThat(measurer.measure("test", RequestRateLimitUnit.TOKENS)).isEqualTo(1L);

        // 8 characters = 2 tokens
        assertThat(measurer.measure("testtest", RequestRateLimitUnit.TOKENS)).isEqualTo(2L);

        // 5 characters = 2 tokens (ceiling of 5/4.0 = ceiling of 1.25)
        assertThat(measurer.measure("testy", RequestRateLimitUnit.TOKENS)).isEqualTo(2L);
    }

    @Test
    @DisplayName("measure estimates tokens minimum of 1")
    void measureEstimatesTokensMinimumOfOne() {
        // Empty string after trimming should still return minimum 1
        assertThat(measurer.measure("a", RequestRateLimitUnit.TOKENS)).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("measure estimates tokens for long text")
    void measureEstimatesTokensForLongText() {
        String text = "This is a longer piece of text that should result in multiple tokens when estimated.";
        long tokens = measurer.measure(text, RequestRateLimitUnit.TOKENS);
        // 84 characters / 4.0 = 21 tokens
        assertThat(tokens).isEqualTo(21L);
    }

    @Test
    @DisplayName("measure handles whitespace in token calculation")
    void measureHandlesWhitespaceInTokenCalculation() {
        // Whitespace is trimmed before calculation
        String textWithWhitespace = "   test   ";
        String textWithoutWhitespace = "test";
        assertThat(measurer.measure(textWithWhitespace, RequestRateLimitUnit.TOKENS))
            .isEqualTo(measurer.measure(textWithoutWhitespace, RequestRateLimitUnit.TOKENS));
    }

    @Test
    @DisplayName("measure tokens uses ceiling division")
    void measureTokensUsesCeilingDivision() {
        // Test ceiling behavior: 1 char = ceiling(1/4) = 1 token
        assertThat(measurer.measure("a", RequestRateLimitUnit.TOKENS)).isEqualTo(1L);

        // 3 chars = ceiling(3/4) = 1 token
        assertThat(measurer.measure("abc", RequestRateLimitUnit.TOKENS)).isEqualTo(1L);

        // 4 chars = ceiling(4/4) = 1 token
        assertThat(measurer.measure("abcd", RequestRateLimitUnit.TOKENS)).isEqualTo(1L);

        // 5 chars = ceiling(5/4) = 2 tokens
        assertThat(measurer.measure("abcde", RequestRateLimitUnit.TOKENS)).isEqualTo(2L);
    }

    // ============ complex/realistic text tests ============

    @Test
    @DisplayName("measure counts words in realistic prompt")
    void measureCountsWordsInRealisticPrompt() {
        String prompt = "Please analyze this text and provide a summary of the main points.";
        long words = measurer.measure(prompt, RequestRateLimitUnit.WORDS);
        assertThat(words).isEqualTo(12L);
    }

    @Test
    @DisplayName("measure estimates tokens for realistic prompt")
    void measureEstimatesTokensForRealisticPrompt() {
        String prompt = "Please analyze this text and provide a summary of the main points.";
        long tokens = measurer.measure(prompt, RequestRateLimitUnit.TOKENS);
        // The prompt has 65 characters, so ceiling(65 / 4.0) = 17 tokens
        assertThat(tokens).isEqualTo(17L);
    }

    @Test
    @DisplayName("measure handles multiline text with WORDS unit")
    void measureHandlesMultilineTextWords() {
        String multiline = "Line one\nLine two\nLine three";
        long words = measurer.measure(multiline, RequestRateLimitUnit.WORDS);
        assertThat(words).isEqualTo(6L);
    }

    @Test
    @DisplayName("measure handles multiline text with TOKENS unit")
    void measureHandlesMultilineTextTokens() {
        String multiline = "Line one\nLine two\nLine three";
        long tokens = measurer.measure(multiline, RequestRateLimitUnit.TOKENS);
        // 28 characters / 4 = 7 tokens
        assertThat(tokens).isEqualTo(7L);
    }

    @Test
    @DisplayName("measure handles unicode characters")
    void measureHandlesUnicodeCharacters() {
        // Unicode characters should still count as 1 unit each
        String unicode = "hello 世界 test";
        long words = measurer.measure(unicode, RequestRateLimitUnit.WORDS);
        assertThat(words).isGreaterThan(0L);
    }

    @Test
    @DisplayName("measure handles very long text")
    void measureHandlesVeryLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("word ");
        }
        String longText = sb.toString();
        long words = measurer.measure(longText, RequestRateLimitUnit.WORDS);
        assertThat(words).isEqualTo(1000L);

        long tokens = measurer.measure(longText, RequestRateLimitUnit.TOKENS);
        assertThat(tokens).isGreaterThan(0L);
    }
}
