package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentReviewStatus;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerSplitAnalysisService;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisItem;
import com.example.ttslab.audiobooks.workflow.speakeranalysis.SpeakerVoiceAnalysisService;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitAnalysisResponse;
import com.example.ttslab.audiobooks.workflow.SpeakerSplitTurn;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpeakerSplitPersistenceService {
    private final SpeakerSplitAnalysisService speakerSplitAnalysisService;
    private final SpeakerVoiceAnalysisService speakerVoiceAnalysisService;
    private final AudiobookSpeechSegmentRepository speechSegmentRepository;

    public SpeakerSplitPersistenceService(
        SpeakerSplitAnalysisService speakerSplitAnalysisService,
        SpeakerVoiceAnalysisService speakerVoiceAnalysisService,
        AudiobookSpeechSegmentRepository speechSegmentRepository
    ) {
        this.speakerSplitAnalysisService = speakerSplitAnalysisService;
        this.speakerVoiceAnalysisService = speakerVoiceAnalysisService;
        this.speechSegmentRepository = speechSegmentRepository;
    }

    @Transactional
    public SpeakerSplitAnalysisResponse splitAndPersist(
        AudiobookProject project,
        String rawDialogue,
        List<SpeakerVoiceAnalysisItem> speakers
    ) {
        speechSegmentRepository.deleteByProjectIdAndSegmentOrigin(project.getId(), AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        List<SpeakerCharacter> characters = speakerVoiceAnalysisService.syncProjectCharacters(project.getId(), speakers);
        SpeakerSplitAnalysisResponse response = speakerSplitAnalysisService.split(rawDialogue, speakers);

        Map<String, SpeakerCharacter> charactersByName = characters.stream()
            .collect(Collectors.toMap(
                character -> normalized(character.getSpeakerName()),
                Function.identity(),
                (left, right) -> left
            ));

        Instant now = Instant.now();
        List<AudiobookSpeechSegment> segments = IntStream.range(0, response.turns().size())
            .mapToObj(index -> toSpeechSegment(project, charactersByName, response.turns().get(index), now, index))
            .toList();

        if (!segments.isEmpty()) {
            speechSegmentRepository.saveAll(segments);
        }
        return response;
    }

    private AudiobookSpeechSegment toSpeechSegment(
        AudiobookProject project,
        Map<String, SpeakerCharacter> charactersByName,
        SpeakerSplitTurn turn,
        Instant now,
        int orderIndex
    ) {
        SpeakerCharacter character = charactersByName.get(normalized(turn.speaker()));
        if (character == null) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "SPEAKER_CHARACTER_NOT_FOUND",
                "The split dialogue referenced an unknown speaker."
            );
        }

        String title = "Speech segment " + (orderIndex + 1);
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            UUID.randomUUID().toString(),
            project,
            orderIndex,
            title,
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            now,
            now,
            null,
            null,
            null,
            null,
            turn.text(),
            null,
            character.getId()
        );
        segment.setSegmentOrigin(AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW);
        return segment;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

