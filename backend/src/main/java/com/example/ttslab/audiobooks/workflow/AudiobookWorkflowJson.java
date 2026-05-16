package com.example.ttslab.audiobooks.workflow;

public final class AudiobookWorkflowJson {
    private AudiobookWorkflowJson() {
    }

    public static String stripMarkdownFence(String answer) {
        String trimmed = answer.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstLineBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineBreak < 0 || lastFence <= firstLineBreak) {
            return trimmed;
        }
        return trimmed.substring(firstLineBreak + 1, lastFence).trim();
    }
}


