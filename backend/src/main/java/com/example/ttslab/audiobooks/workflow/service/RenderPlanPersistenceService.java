package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegment;
import com.example.ttslab.audiobooks.model.AudiobookSpeechSegmentOrigin;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.audiobooks.repository.AudiobookSpeechSegmentRepository;
import com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderPlanResponse;
import com.example.ttslab.audiobooks.workflow.SingleSpeakerRenderRequest;
import com.example.ttslab.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RenderPlanPersistenceService {
    private final AudiobookProjectRepository projectRepository;
    private final AudiobookSpeechSegmentRepository segmentRepository;

    public RenderPlanPersistenceService(
        AudiobookProjectRepository projectRepository,
        AudiobookSpeechSegmentRepository segmentRepository
    ) {
        this.projectRepository = projectRepository;
        this.segmentRepository = segmentRepository;
    }

    @Transactional(readOnly = true)
    public SingleSpeakerRenderPlanResponse loadRenderPlanFromDatabase(String projectId) {
        AudiobookProject project = projectRepository.findWithDetailsById(projectId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "PROJECT_NOT_FOUND",
                "Project not found with ID: " + projectId
            ));

        if (project.getProductionModelName() == null || project.getProductionModelName().isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "PROJECT_MISSING_MODEL_NAME",
                "Project does not have a model name configured for audio generation."
            );
        }

        if (project.getProductionLanguageCode() == null || project.getProductionLanguageCode().isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "PROJECT_MISSING_LANGUAGE_CODE",
                "Project does not have a language code configured for audio generation."
            );
        }

        List<AudiobookSpeechSegment> segments = project.getSpeechSegments().stream()
            .filter(s -> s.getSegmentOrigin() == AudiobookSpeechSegmentOrigin.SCRIPT_PREVIEW)
            .toList();

        if (segments.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "NO_SEGMENTS_TO_RENDER",
                "Project has no script preview segments to render."
            );
        }

        List<SingleSpeakerRenderRequest> renderRequests = segments.stream()
            .map(segment -> convertSegmentToRenderRequest(segment, project))
            .toList();

        return new SingleSpeakerRenderPlanResponse(renderRequests);
    }

    private SingleSpeakerRenderRequest convertSegmentToRenderRequest(
        AudiobookSpeechSegment segment,
        AudiobookProject project
    ) {
        String text = segment.getStyledText();
        if (text == null || text.isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "SEGMENT_MISSING_STYLED_TEXT",
                "Segment '" + segment.getTitle() + "' does not have styled text. " +
                    "Please apply styling to all segments before generating audio."
            );
        }

        Map<String, Object> input = new HashMap<>();
        input.put("text", text);
        input.put("segmentOrderIndex", segment.getOrderIndex());

        Map<String, Object> voice = new HashMap<>();
        voice.put("languageCode", project.getProductionLanguageCode());
        voice.put("modelName", project.getProductionModelName());
        voice.put("speakerName", segment.getCharacter().getSpeakerName().trim());
        voice.put("name", segment.getCharacter().getVoiceSuggestion());

        Map<String, Object> audioConfig = new HashMap<>();
        audioConfig.put("audioEncoding", "MP3");

        return new SingleSpeakerRenderRequest(input, voice, audioConfig);
    }

}
