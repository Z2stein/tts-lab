package com.example.ttslab.common;

import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationEstimatorTest {

  @Test
  void estimatesShortText() {
    Integer duration = DurationEstimator.estimateSpeakingDurationSeconds("Hello world");
    // 2 words: (2 * 60) / 150 = 0.8 → 0 → max(1, 0) = 1 second
    assertEquals(1, duration);
  }

  @Test
  void estimatesMediumText() {
    String text = String.join(" ", Collections.nCopies(47, "word"));
    Integer duration = DurationEstimator.estimateSpeakingDurationSeconds(text);
    // 47 words: (47 * 60) / 150 = 18.8 → 18 seconds
    assertTrue(duration >= 18 && duration <= 19, "Expected 18-19 seconds, got " + duration);
  }

  @Test
  void estimatesLongText() {
    String text = String.join(" ", Collections.nCopies(150, "word"));
    Integer duration = DurationEstimator.estimateSpeakingDurationSeconds(text);
    // 150 words: (150 * 60) / 150 = 60 seconds
    assertEquals(60, (int) duration);
  }

  @Test
  void estimatesThreeMinutesForThreeHundredWords() {
    String text = String.join(" ", Collections.nCopies(300, "word"));
    Integer duration = DurationEstimator.estimateSpeakingDurationSeconds(text);
    // 300 words: (300 * 60) / 150 = 120 seconds = 2 minutes
    assertEquals(120, (int) duration);
  }

  @Test
  void returnsNullForEmptyText() {
    assertNull(DurationEstimator.estimateSpeakingDurationSeconds(""));
  }

  @Test
  void returnsNullForNullText() {
    assertNull(DurationEstimator.estimateSpeakingDurationSeconds(null));
  }

  @Test
  void returnsNullForWhitespaceOnly() {
    assertNull(DurationEstimator.estimateSpeakingDurationSeconds("   \n\t  "));
  }

  @Test
  void handlesVariousWordCounts() {
    // Test boundary conditions
    assertEquals(1, DurationEstimator.estimateSpeakingDurationSeconds("word"));  // 1 word → 1 sec
    assertEquals(2, DurationEstimator.estimateSpeakingDurationSeconds(String.join(" ", Collections.nCopies(5, "word"))));  // 5 words → 2 sec
    assertEquals(6, DurationEstimator.estimateSpeakingDurationSeconds(String.join(" ", Collections.nCopies(15, "word"))));  // 15 words → 6 sec
    assertEquals(12, DurationEstimator.estimateSpeakingDurationSeconds(String.join(" ", Collections.nCopies(30, "word"))));  // 30 words → 12 sec
  }
}
