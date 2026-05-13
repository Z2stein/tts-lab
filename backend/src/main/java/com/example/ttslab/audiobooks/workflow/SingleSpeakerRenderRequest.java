package com.example.ttslab.audiobooks.workflow;

import java.util.Map;

public record SingleSpeakerRenderRequest(
    Map<String, Object> input,
    Map<String, Object> voice,
    Map<String, Object> audioConfig
) {
}

