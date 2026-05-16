package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.audiobooks.workflow.AnnotatedSpeakerTurn;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitTurn;
import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DeterministicAudiobookWorkflowFallbackService {
    private static final Pattern SPEAKER_LINE = Pattern.compile("^\\s*([\\p{L}][\\p{L}0-9 ._'â€™-]{0,40})\\s*[:ï¼š-]\\s+(.+)\\s*$");
    private static final Pattern LEADING_NON_TITLE_CHARS = Pattern.compile("^[^\\p{L}\\p{N}]+");
    private static final List<SpeakerVoice> MOCK_VOICES = List.of(SpeakerVoice.values());

    public List<SpeakerVoiceAnalysisItem> analyzeSpeakers(String rawDialogue) {
        Map<String, Integer> speakerOrder = new LinkedHashMap<>();
        for (String line : rawDialogue.split("\\R")) {
            Matcher matcher = SPEAKER_LINE.matcher(line);
            if (matcher.matches()) {
                String speakerName = matcher.group(1).trim();
                speakerOrder.putIfAbsent(speakerName, speakerOrder.size());
            }
        }

        if (speakerOrder.isEmpty()) {
            return List.of(new SpeakerVoiceAnalysisItem(
                "Narrator",
                "Narrates dialogue without explicit speaker labels",
                MOCK_VOICES.getFirst()
            ));
        }

        List<SpeakerVoiceAnalysisItem> items = new ArrayList<>();
        speakerOrder.forEach((speakerName, index) -> items.add(new SpeakerVoiceAnalysisItem(
            speakerName,
            "Detected dialogue speaker",
            MOCK_VOICES.get(index % MOCK_VOICES.size())
        )));
        return items;
    }

    public String suggestProjectTitle(String rawDialogue) {
        String candidate = firstMeaningfulLine(rawDialogue);
        if (candidate.isBlank()) {
            return "Untitled audiobook";
        }

        Matcher speakerMatcher = SPEAKER_LINE.matcher(candidate);
        if (speakerMatcher.matches()) {
            candidate = speakerMatcher.group(2).trim();
        }

        String[] words = candidate
            .replaceAll("[^\\p{L}\\p{N} ]+", " ")
            .trim()
            .split("\\s+");

        if (words.length == 0 || words[0].isBlank()) {
            return "Untitled audiobook";
        }

        int limit = Math.min(6, words.length);
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            String word = words[i];
            if (word.isBlank()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                title.append(word.substring(1).toLowerCase());
            }
        }

        String normalized = title.toString().trim();
        return normalized.isBlank() ? "Untitled audiobook" : normalized;
    }

    public List<SpeakerSplitTurn> splitDialogue(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.isBlank()) {
            return List.of();
        }

        List<SpeakerSplitTurn> turns = new ArrayList<>();
        SpeakerSplitTurn currentTurn = null;
        for (String line : rawDialogue.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }

            Matcher matcher = SPEAKER_LINE.matcher(line);
            if (matcher.matches()) {
                currentTurn = new SpeakerSplitTurn(matcher.group(1).trim(), matcher.group(2).trim());
                turns.add(currentTurn);
            } else if (currentTurn != null) {
                currentTurn = new SpeakerSplitTurn(currentTurn.speaker(), currentTurn.text() + " " + line.trim());
                turns.set(turns.size() - 1, currentTurn);
            } else {
                currentTurn = new SpeakerSplitTurn("Narrator", line.trim());
                turns.add(currentTurn);
            }
        }

        return turns;
    }

    public List<AnnotatedSpeakerTurn> annotateEmotions(List<SpeakerSplitTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }

        return turns.stream()
            .map(turn -> new AnnotatedSpeakerTurn(turn.speaker(), annotateText(turn.text())))
            .toList();
    }

    private String annotateText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String trimmed = text.trim();
        String lower = trimmed.toLowerCase();
        String marked = trimmed;

        if (lower.contains(" and now ")) {
            marked = marked.replaceFirst("(?i)\\s+and now\\s+", " [short pause] and now ");
        } else if (trimmed.contains(". ")) {
            marked = marked.replaceFirst("\\.\\s+", ". [short pause] ");
        }

        if (lower.contains("sigh")) {
            return "[sigh] " + marked;
        }
        if (lower.contains("fine") || lower.contains("happy") || lower.contains("great") || lower.contains("wonderful")) {
            if (lower.contains("!") || lower.contains("cannot") || lower.contains("can't") || lower.contains("believe")) {
                marked = marked.replaceFirst("(?i)(cannot|can't|I cannot|I can't)", "[urgent] $1");
            }
            return "[happy] " + marked;
        }
        if (lower.contains("please") || lower.contains("talk") || lower.contains("know") || lower.contains("calm")) {
            return "[calm] " + marked;
        }
        if (lower.contains("!") || lower.contains("urgent") || lower.matches(".*\\bnow\\b.*") || lower.contains("cannot") || lower.contains("can't")) {
            return "[urgent] " + marked;
        }
        if (lower.contains("hurt") || lower.contains("sad") || lower.contains("sorry")) {
            return "[sad] " + marked;
        }
        return "[calm] " + marked;
    }

    private String firstMeaningfulLine(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.isBlank()) {
            return "";
        }
        return rawDialogue.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .map(line -> LEADING_NON_TITLE_CHARS.matcher(line).replaceFirst(""))
            .findFirst()
            .orElse("");
    }
}
