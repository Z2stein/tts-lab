package com.example.ttslab.projects.ttsworkbench;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProviderCompatibleRequestPlanner {
    static final int PROVIDER_SUPPORTED_SPEAKER_LIMIT = 2;

    private final ObjectMapper objectMapper;

    public ProviderCompatibleRequestPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProviderCompatibleRequestPlanResponse plan(ProviderCompatibleRequestPlanRequest request) {
        if (request == null || request.input() == null) {
            return new ProviderCompatibleRequestPlanResponse(List.of());
        }

        List<Map<String, Object>> turns = turns(request.input());
        if (turns.isEmpty()) {
            return new ProviderCompatibleRequestPlanResponse(List.of());
        }

        List<ProviderCompatibleRequestChunk> chunks = new ArrayList<>();
        List<Map<String, Object>> currentTurns = new ArrayList<>();
        Set<String> currentSpeakers = new LinkedHashSet<>();

        for (Map<String, Object> turn : turns) {
            String speaker = stringValue(turn.get("speaker"));
            boolean newSpeakerWouldExceedLimit = !speaker.isBlank()
                && !currentSpeakers.contains(speaker)
                && currentSpeakers.size() >= PROVIDER_SUPPORTED_SPEAKER_LIMIT;

            if (!currentTurns.isEmpty() && newSpeakerWouldExceedLimit) {
                chunks.add(buildChunk(chunks.size() + 1, currentSpeakers, currentTurns, request));
                currentTurns = new ArrayList<>();
                currentSpeakers = new LinkedHashSet<>();
            }

            currentTurns.add(deepCopyMap(turn));
            if (!speaker.isBlank()) {
                currentSpeakers.add(speaker);
            }
        }

        if (!currentTurns.isEmpty()) {
            chunks.add(buildChunk(chunks.size() + 1, currentSpeakers, currentTurns, request));
        }

        return new ProviderCompatibleRequestPlanResponse(chunks);
    }

    private ProviderCompatibleRequestChunk buildChunk(
        int chunkNumber,
        Set<String> speakers,
        List<Map<String, Object>> turns,
        ProviderCompatibleRequestPlanRequest source
    ) {
        Map<String, Object> input = deepCopyMap(source.input());
        Map<String, Object> markup = nestedMap(source.input(), "multiSpeakerMarkup");
        markup.put("turns", turns.stream().map(this::deepCopyMap).toList());
        input.put("multiSpeakerMarkup", markup);

        Map<String, Object> voice = source.voice() == null ? new LinkedHashMap<>() : deepCopyMap(source.voice());
        Map<String, Object> multiSpeakerVoiceConfig = nestedMap(source.voice(), "multiSpeakerVoiceConfig");
        multiSpeakerVoiceConfig.put("speakerVoiceConfigs", speakerVoiceConfigs(source.voice(), speakers));
        voice.put("multiSpeakerVoiceConfig", multiSpeakerVoiceConfig);

        Map<String, Object> audioConfig = source.audioConfig() == null ? new LinkedHashMap<>() : deepCopyMap(source.audioConfig());

        return new ProviderCompatibleRequestChunk(
            chunkNumber,
            List.copyOf(speakers),
            new FinalTtsRequestPreviewResponse(input, voice, audioConfig)
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

    private List<Map<String, Object>> speakerVoiceConfigs(Map<String, Object> voice, Set<String> speakers) {
        if (voice == null || speakers.isEmpty()) {
            return List.of();
        }

        Object multiSpeakerVoiceConfig = voice.get("multiSpeakerVoiceConfig");
        if (!(multiSpeakerVoiceConfig instanceof Map<?, ?> multiSpeakerVoiceConfigMap)) {
            return List.of();
        }

        Object configs = multiSpeakerVoiceConfigMap.get("speakerVoiceConfigs");
        if (!(configs instanceof List<?> configList)) {
            return List.of();
        }

        return configList.stream()
            .filter(Map.class::isInstance)
            .map(config -> objectMapper.convertValue(config, new TypeReference<Map<String, Object>>() {}))
            .filter(config -> speakers.contains(stringValue(config.get("speakerAlias"))))
            .map(this::deepCopyMap)
            .toList();
    }

    private Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        if (source == null || !(source.get(key) instanceof Map<?, ?> nested)) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(nested, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> deepCopyMap(Map<String, Object> map) {
        return objectMapper.convertValue(map, new TypeReference<Map<String, Object>>() {});
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
