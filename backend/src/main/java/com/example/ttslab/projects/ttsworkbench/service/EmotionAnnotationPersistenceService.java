package com.example.ttslab.projects.ttsworkbench.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.projects.ttsworkbench.AnnotatedSpeakerTurn;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmotionAnnotationPersistenceService {
    private final AudiobookSpeechSegmentRepository speechSegmentRepository;

    public EmotionAnnotationPersistenceService(AudiobookSpeechSegmentRepository speechSegmentRepository) {
        this.speechSegmentRepository = speechSegmentRepository;
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
            segment.setUpdatedAt(now);
        }

        speechSegmentRepository.saveAll(segments);
    }
}
