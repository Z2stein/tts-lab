package com.example.ttslab.common;

public class DurationEstimator {
  private static final int WORDS_PER_MINUTE = 150;

  private DurationEstimator() {}

  public static Integer estimateSpeakingDurationSeconds(String text) {
    if (text == null || text.trim().isEmpty()) {
      return null;
    }
    int wordCount = text.trim().split("\\s+").length;
    int minutes = Math.max(1, wordCount / WORDS_PER_MINUTE);
    return minutes * 60;
  }
}
