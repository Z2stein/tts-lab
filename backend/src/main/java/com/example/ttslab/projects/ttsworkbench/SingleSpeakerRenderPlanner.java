package com.example.ttslab.projects.ttsworkbench;

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

        Map<String, String> voiceIdsBySpeaker = voiceIdsBySpeaker(request.voice());
        String languageCode = stringValue(request.voice() == null ? null : request.voice().get("languageCode"));
        String modelName = stringValue(request.voice() == null ? null : request.voice().get("modelName"));
        String audioEncoding = stringValue(request.audioConfig() == null ? null : request.audioConfig().get("audioEncoding"));

        List<SingleSpeakerRenderRequest> renderRequests = new ArrayList<>();
        RenderGroup currentGroup = null;

        for (int turnIndex = 0; turnIndex < turns.size(); turnIndex++) {
            Map<String, Object> turn = turns.get(turnIndex);
            String speakerName = stringValue(turn.get("speaker"));
            String text = stringValue(turn.get("text"));

            if (currentGroup == null || !currentGroup.speakerName().equals(speakerName)) {
                if (currentGroup != null) {
                    renderRequests.add(toRenderRequest(renderRequests.size() + 1, currentGroup, voiceIdsBySpeaker, languageCode, modelName, audioEncoding));
                }
                currentGroup = new RenderGroup(speakerName);
            }

            currentGroup.addTurn(turnIndex, text);
        }

        if (currentGroup != null) {
            renderRequests.add(toRenderRequest(renderRequests.size() + 1, currentGroup, voiceIdsBySpeaker, languageCode, modelName, audioEncoding));
        }

        return new SingleSpeakerRenderPlanResponse(renderRequests);
    }

    private SingleSpeakerRenderRequest toRenderRequest(
        int renderIndex,
        RenderGroup group,
        Map<String, String> voiceIdsBySpeaker,
        String languageCode,
        String modelName,
        String audioEncoding
    ) {
        return new SingleSpeakerRenderRequest(
            renderIndex,
            group.originalTurnIndexes(),
            group.speakerName(),
            voiceIdsBySpeaker.getOrDefault(group.speakerName(), ""),
            group.text(),
            languageCode,
            modelName,
            audioEncoding
        );
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

    private Map<String, String> voiceIdsBySpeaker(Map<String, Object> voice) {
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

        Map<String, String> voiceIdsBySpeaker = new LinkedHashMap<>();
        for (Object config : configList) {
            if (config instanceof Map<?, ?> configMap) {
                String speakerAlias = stringValue(configMap.get("speakerAlias"));
                if (!speakerAlias.isBlank()) {
                    voiceIdsBySpeaker.put(speakerAlias, stringValue(configMap.get("speakerId")));
                }
            }
        }
        return voiceIdsBySpeaker;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static final class RenderGroup {
        private final String speakerName;
        private final List<Integer> originalTurnIndexes = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();

        private RenderGroup(String speakerName) {
            this.speakerName = speakerName;
        }

        private String speakerName() {
            return speakerName;
        }

        private void addTurn(int originalTurnIndex, String text) {
            originalTurnIndexes.add(originalTurnIndex);
            texts.add(text);
        }

        private List<Integer> originalTurnIndexes() {
            return List.copyOf(originalTurnIndexes);
        }

        private String text() {
            return String.join("\n", texts);
        }
    }
}
