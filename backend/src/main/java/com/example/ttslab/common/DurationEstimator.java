package com.example.ttslab.common;

public class DurationEstimator {
  private static final int WORDS_PER_MINUTE = 150;

  private DurationEstimator() {}

  public static Integer estimateSpeakingDurationSeconds(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    int wordCount = text.trim().split("\\s+").length;
    // Calculate seconds directly: (wordCount / 150) * 60 = (wordCount * 60) / 150
    // This ensures short text gets correct duration (e.g., 47 words = 19 seconds)
    // instead of being forced to minimum 1 minute
    int seconds = Math.max(1, (wordCount * 60) / WORDS_PER_MINUTE);
    return seconds;
  }
}
