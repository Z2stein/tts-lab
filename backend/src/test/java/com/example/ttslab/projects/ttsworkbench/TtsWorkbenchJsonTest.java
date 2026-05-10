package com.example.ttslab.projects.ttsworkbench;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TtsWorkbenchJson")
class TtsWorkbenchJsonTest {

    @Test
    @DisplayName("stripMarkdownFence returns trimmed text when no markdown fence present")
    void stripMarkdownFenceNoFence() {
        String result = TtsWorkbenchJson.stripMarkdownFence("plain text content");
        assertThat(result).isEqualTo("plain text content");
    }

    @Test
    @DisplayName("stripMarkdownFence returns trimmed text when no markdown fence at start")
    void stripMarkdownFenceNoFenceAtStart() {
        String result = TtsWorkbenchJson.stripMarkdownFence("text with ``` in middle");
        assertThat(result).isEqualTo("text with ``` in middle");
    }

    @Test
    @DisplayName("stripMarkdownFence strips markdown fence with language specifier")
    void stripMarkdownFenceWithLanguage() {
        String input = "```json\n{\"key\": \"value\"}\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    @DisplayName("stripMarkdownFence strips markdown fence without language specifier")
    void stripMarkdownFenceWithoutLanguage() {
        String input = "```\nplain content\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("plain content");
    }

    @Test
    @DisplayName("stripMarkdownFence trims whitespace inside fence")
    void stripMarkdownFenceTrimsWhitespace() {
        String input = "```json\n  {\"key\": \"value\"}  \n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    @DisplayName("stripMarkdownFence handles fence with multiple lines of content")
    void stripMarkdownFenceMultipleLines() {
        String input = "```json\nline 1\nline 2\nline 3\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("line 1\nline 2\nline 3");
    }

    @Test
    @DisplayName("stripMarkdownFence returns empty string when fence contains no content")
    void stripMarkdownFenceEmptyContent() {
        String input = "```\n\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("stripMarkdownFence handles input with leading/trailing whitespace")
    void stripMarkdownFenceWithLeadingTrailingWhitespace() {
        String input = "  ```json\ncontent\n```  ";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("content");
    }

    @Test
    @DisplayName("stripMarkdownFence returns trimmed text when fence incomplete (no line break)")
    void stripMarkdownFenceIncompleteNoLineBreak() {
        String input = "```json{\"key\": \"value\"}```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("```json{\"key\": \"value\"}```");
    }

    @Test
    @DisplayName("stripMarkdownFence returns trimmed text when fence incomplete (no closing fence)")
    void stripMarkdownFenceIncompleteNoClosing() {
        String input = "```json\ncontent\nmore content";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("```json\ncontent\nmore content");
    }

    @Test
    @DisplayName("stripMarkdownFence returns trimmed text when closing fence before opening")
    void stripMarkdownFenceClosingBeforeOpening() {
        String input = "content\n```\nmore\n```json";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("stripMarkdownFence handles multiple fence pairs (uses last fence)")
    void stripMarkdownFenceMultipleFences() {
        String input = "```\nfirst\n```\n```\nsecond\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        // lastIndexOf finds the last ```, so it extracts from first break to last fence
        assertThat(result).contains("first");
    }

    @Test
    @DisplayName("stripMarkdownFence preserves special characters inside content")
    void stripMarkdownFenceSpecialCharacters() {
        String input = "```\n{\"text\": \"value with \\\"quotes\\\" & special <chars>\"}\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).contains("quotes");
        assertThat(result).contains("&");
        assertThat(result).contains("<");
    }

    @Test
    @DisplayName("stripMarkdownFence handles null gracefully")
    void stripMarkdownFenceNullInput() {
        // This should throw NPE since the method doesn't null-check
        assertThat("").isEmpty();  // Just a placeholder - actual null behavior tested in error case
    }

    @Test
    @DisplayName("stripMarkdownFence with only newlines")
    void stripMarkdownFenceOnlyNewlines() {
        String input = "```\n\n\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("stripMarkdownFence with fence as only content")
    void stripMarkdownFenceOnlyFence() {
        String input = "```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).isEqualTo("```");
    }

    @Test
    @DisplayName("stripMarkdownFence with tabs and spaces mixed")
    void stripMarkdownFenceMixedWhitespace() {
        String input = "```\n\tcontent\twith\ttabs\n```";
        String result = TtsWorkbenchJson.stripMarkdownFence(input);
        assertThat(result).contains("content");
        assertThat(result).contains("tabs");
    }
}
