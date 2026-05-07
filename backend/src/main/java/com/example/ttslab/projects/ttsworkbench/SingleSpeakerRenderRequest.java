package com.example.ttslab.projects.ttsworkbench;

import java.util.List;

public record SingleSpeakerRenderRequest(
    int renderIndex,
    List<Integer> originalTurnIndexes,
    String speakerName,
    String voiceId,
    String text,
    String languageCode,
    String modelName,
    String audioEncoding
) {
}
