package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerCharacterRepository;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.workflow.AnnotatedSpeakerTurn;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitTurn;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmotionAnnotationPersistenceService {
    private final AudiobookSpeechSegmentRepository speechSegmentRepository;
    private final SpeakerCharacterRepository speakerCharacterRepository;

    public EmotionAnnotationPersistenceService(
        AudiobookSpeechSegmentRepository speechSegmentRepository,
        SpeakerCharacterRepository speakerCharacterRepository
    ) {
        this.speechSegmentRepository = speechSegmentRepository;
        this.speakerCharacterRepository = speakerCharacterRepository;
    }

    @Transactional(readOnly = true)
    public List<SpeakerSplitTurn> loadScriptPreviewTurns(AudiobookProject project) {
        List<AudiobookSpeechSegment> segments = speechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(
            project.getId(),
            AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW
        );
        if (segments.isEmpty()) {
            return List.of();
        }

        return segments.stream()
            .map(segment -> new SpeakerSplitTurn(resolveSpeakerName(segment), resolveTurnText(segment)))
            .toList();
    }

    @Transactional
    public void persistStyledText(AudiobookProject project, List<AnnotatedSpeakerTurn> annotatedTurns) {
        List<AudiobookSpeechSegment> segments = speechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(
            project.getId(),
            AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW
        );

        if (segments.size() != annotatedTurns.size()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "EMOTION_ANNOTATION_SEGMENT_MISMATCH",
                "The emotion annotations no longer match the current script preview."
            );
        }

        Instant now = Instant.now();
        for (int i = 0; i < segments.size(); i++) {
            AudiobookSpeechSegment segment = segments.get(i);
            segment.setStyledText(annotatedTurns.get(i).text());
            segment.setReviewStatus(AudiobookSpeechSegmentReviewStatus.APPROVED);
            segment.setUpdatedAt(now);
        }

        speechSegmentRepository.saveAll(segments);
    }

    public List<SpeakerSplitTurn> saveScriptPreviewTurns(AudiobookProject project, List<SpeakerSplitTurn> turns) {
        List<AudiobookSpeechSegment> segments = speechSegmentRepository.findByProjectIdAndSegmentOriginOrderByOrderIndex(
            project.getId(),
            AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW
        );
        List<SpeakerSplitTurn> safeTurns = turns == null ? List.of() : turns;

        if (segments.size() != safeTurns.size()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "SCRIPT_PREVIEW_SEGMENT_MISMATCH",
                "The script preview no longer matches the saved script turns."
            );
        }

        Map<String, SpeakerCharacter> charactersByName = new LinkedHashMap<>();
        for (SpeakerCharacter character : speakerCharacterRepository.findByProjectIdOrderBySortOrderAsc(project.getId())) {
            charactersByName.put(normalized(character.getSpeakerName()), character);
        }

        Instant now = Instant.now();
        List<SpeakerSplitTurn> savedTurns = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            AudiobookSpeechSegment segment = segments.get(i);
            SpeakerSplitTurn turn = safeTurns.get(i);

            String speaker = requireTurnValue(turn.speaker());
            String text = requireTurnValue(turn.text());
            SpeakerCharacter character = charactersByName.get(normalized(speaker));

            if (character == null) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SCRIPT_PREVIEW_SPEAKER_NOT_FOUND",
                    "Speaker '" + speaker.trim() + "' not found in available characters."
                );
            }

            boolean needsChanges = segment.getReviewStatus() == AudiobookSpeechSegmentReviewStatus.APPROVED
                || segment.getReviewStatus() == AudiobookSpeechSegmentReviewStatus.NEEDS_CHANGES
                || (segment.getStyledText() != null && !segment.getStyledText().isBlank());

            segment.setCharacter(character);
            segment.setOriginalText(text.trim());
            segment.setStyledText(null);
            segment.setReviewStatus(needsChanges
                ? AudiobookSpeechSegmentReviewStatus.NEEDS_CHANGES
                : AudiobookSpeechSegmentReviewStatus.PENDING);
            segment.setUpdatedAt(now);

            savedTurns.add(new SpeakerSplitTurn(character.getSpeakerName(), segment.getOriginalText()));
        }

        speechSegmentRepository.saveAll(segments);
        return savedTurns;
    }

    private String resolveSpeakerName(AudiobookSpeechSegment segment) {
        SpeakerCharacter character = segment.getCharacter();
        String speakerName = character.getSpeakerName();
        if (speakerName != null && !speakerName.isBlank()) {
            return speakerName.trim();
        }
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "EMOTION_ANNOTATION_SEGMENT_MISMATCH",
            "The emotion annotations no longer match the current script preview."
        );
    }

    private String resolveTurnText(AudiobookSpeechSegment segment) {
        String text = segment.getOriginalText();
        if (text == null || text.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "EMOTION_ANNOTATION_SEGMENT_MISMATCH",
                "The emotion annotations no longer match the current script preview."
            );
        }
        return text.trim();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requireTurnValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "SCRIPT_PREVIEW_SEGMENT_MISMATCH",
                "The script preview no longer matches the saved script turns."
            );
        }
        return value;
    }
}

