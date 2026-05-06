package com.example.ttslab.projects.ttsworkbench;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FinalTtsRequestBuilder {
    public FinalTtsRequestPreviewResponse build(FinalTtsRequestPreviewRequest request) {
        List<SpeakerVoiceAnalysisItem> speakers = request.speakers() == null ? List.of() : request.speakers();
        List<AnnotatedSpeakerTurn> annotatedTurns = request.annotatedTurns() == null ? List.of() : request.annotatedTurns();

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", defaultIfBlank(request.prompt(), "TTS Workbench generated multi-speaker dialogue."));
        input.put("multiSpeakerMarkup", Map.of("turns", annotatedTurns));

        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("languageCode", defaultIfBlank(request.languageCode(), "en-US"));
        voice.put("modelName", defaultIfBlank(request.modelName(), "{{google-model}}"));
        voice.put("multiSpeakerVoiceConfig", Map.of("speakerVoiceConfigs", speakerVoiceConfigs(speakers)));

        Map<String, Object> audioConfig = new LinkedHashMap<>();
        audioConfig.put("audioEncoding", defaultIfBlank(request.audioEncoding(), "MP3"));

        return new FinalTtsRequestPreviewResponse(input, voice, audioConfig);
    }

    private List<Map<String, String>> speakerVoiceConfigs(List<SpeakerVoiceAnalysisItem> speakers) {
        return speakers.stream()
            .map(speaker -> {
                Map<String, String> speakerVoiceConfig = new LinkedHashMap<>();
                speakerVoiceConfig.put("speakerAlias", speaker.speakerName());
                speakerVoiceConfig.put("speakerId", defaultIfBlank(speaker.voiceSuggestion().getKey(), SpeakerVoice.ERINOME.getKey()));
                return speakerVoiceConfig;
            })
            .toList();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
