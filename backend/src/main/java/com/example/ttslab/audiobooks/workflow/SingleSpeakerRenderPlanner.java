package com.example.ttslab.audiobooks.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SingleSpeakerRenderPlanner {
    private final ObjectMapper objectMapper;

    public SingleSpeakerRenderPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SingleSpeakerRenderPlanResponse plan(SingleSpeakerRenderPlanRequest request) {
        if (request == null || request.input() == null) {
            return new SingleSpeakerRenderPlanResponse(List.of());
        }

        List<Map<String, Object>> turns = turns(request.input());
        if (turns.isEmpty()) {
            return new SingleSpeakerRenderPlanResponse(List.of());
        }

        Map<String, String> voiceNamesBySpeaker = voiceNamesBySpeaker(request.voice());
        String languageCode = stringValue(request.voice() == null ? null : request.voice().get("languageCode"));
        String modelName = stringValue(request.voice() == null ? null : request.voice().get("modelName"));
        String audioEncoding = stringValue(request.audioConfig() == null ? null : request.audioConfig().get("audioEncoding"));

        List<SingleSpeakerRenderRequest> renderRequests = new ArrayList<>();
        for (int turnIndex = 0; turnIndex < turns.size(); turnIndex++) {
            Map<String, Object> turn = turns.get(turnIndex);
            String speakerName = stringValue(turn.get("speaker"));
            String text = stringValue(turn.get("text"));
            renderRequests.add(toRenderRequest(turnIndex, speakerName, text, voiceNamesBySpeaker, languageCode, modelName, audioEncoding));
        }

        return new SingleSpeakerRenderPlanResponse(renderRequests);
    }

    private SingleSpeakerRenderRequest toRenderRequest(
        int segmentOrderIndex,
        String speakerName,
        String text,
        Map<String, String> voiceNamesBySpeaker,
        String languageCode,
        String modelName,
        String audioEncoding
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", text);
        input.put("segmentOrderIndex", segmentOrderIndex);

        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("languageCode", languageCode);
        voice.put("speakerName", speakerName);
        voice.put("name", voiceNamesBySpeaker.getOrDefault(speakerName, ""));
        voice.put("modelName", modelName);

        Map<String, Object> audioConfig = new LinkedHashMap<>();
        audioConfig.put("audioEncoding", audioEncoding);

        return new SingleSpeakerRenderRequest(input, voice, audioConfig);
    }

    private List<Map<String, Object>> turns(Map<String, Object> input) {
        Object markup = input.get("multiSpeakerMarkup");
        if (!(markup instanceof Map<?, ?> markupMap)) {
            return List.of();
        }

        Object turns = markupMap.get("turns");
        if (!(turns instanceof List<?> turnList)) {
            return List.of();
        }

        return turnList.stream()
            .filter(Map.class::isInstance)
            .map(turn -> objectMapper.convertValue(turn, new TypeReference<Map<String, Object>>() {}))
            .toList();
    }

    private Map<String, String> voiceNamesBySpeaker(Map<String, Object> voice) {
        if (voice == null) {
            return Map.of();
        }

        Object multiSpeakerVoiceConfig = voice.get("multiSpeakerVoiceConfig");
        if (!(multiSpeakerVoiceConfig instanceof Map<?, ?> multiSpeakerVoiceConfigMap)) {
            return Map.of();
        }

        Object configs = multiSpeakerVoiceConfigMap.get("speakerVoiceConfigs");
        if (!(configs instanceof List<?> configList)) {
            return Map.of();
        }

        Map<String, String> voiceNamesBySpeaker = new LinkedHashMap<>();
        for (Object config : configList) {
            if (config instanceof Map<?, ?> configMap) {
                String speakerAlias = stringValue(configMap.get("speakerAlias"));
                if (!speakerAlias.isBlank()) {
                    voiceNamesBySpeaker.put(speakerAlias, stringValue(configMap.get("speakerId")));
                }
            }
        }
        return voiceNamesBySpeaker;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}

