package com.example.ttslab.projects.ttsworkbench;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TtsWorkbenchService {

    public SpeakerVoiceAnalysisResponse analyzeSpeakerVoices(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.isBlank()) {
            return new SpeakerVoiceAnalysisResponse(List.of());
        }

        Set<String> speakerNames = new LinkedHashSet<>();
        for (String line : rawDialogue.split("\\R")) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }

            String speakerName = line.substring(0, separatorIndex).trim();
            if (!speakerName.isEmpty()) {
                speakerNames.add(speakerName);
            }
        }

        List<SpeakerVoiceSuggestion> speakers = speakerNames.stream()
            .map(speakerName -> new SpeakerVoiceSuggestion(
                speakerName,
                "Dialogue speaker detected from the script.",
                "Use a clear, natural voice and adjust tone based on the line context."
            ))
            .toList();

        return new SpeakerVoiceAnalysisResponse(speakers);
    }
}
