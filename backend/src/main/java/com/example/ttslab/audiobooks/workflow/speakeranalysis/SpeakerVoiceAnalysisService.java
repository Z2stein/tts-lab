package com.example.ttslab.audiobooks.workflow.speakeranalysis;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.model.SpeakerCharacter;
import com.example.ttslab.audiobooks.repository.AudiobookProjectRepository;
import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatService;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowJson;
import com.example.ttslab.audiobooks.workflow.AudiobookWorkflowPromptProvider;
import com.example.ttslab.audiobooks.workflow.service.DeterministicAudiobookWorkflowFallbackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpeakerVoiceAnalysisService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final Logger log = LoggerFactory.getLogger(SpeakerVoiceAnalysisService.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final AudiobookWorkflowPromptProvider promptProvider;
    private final DeterministicAudiobookWorkflowFallbackService fallbackService;
    private final AudiobookProjectRepository audiobookProjectRepository;
    private final SpeakerCharacterRepository speakerCharacterRepository;
    private final String chatbotProvider;

    @Autowired
    public SpeakerVoiceAnalysisService(
        ChatService chatService,
        ObjectMapper objectMapper,
        AudiobookWorkflowPromptProvider promptProvider,
        DeterministicAudiobookWorkflowFallbackService fallbackService,
        AudiobookProjectRepository audiobookProjectRepository,
        SpeakerCharacterRepository speakerCharacterRepository,
        ChatbotProperties chatbotProperties
    ) {
        this(
            chatService,
            objectMapper,
            promptProvider,
            fallbackService,
            audiobookProjectRepository,
            speakerCharacterRepository,
            chatbotProperties == null ? "mock" : chatbotProperties.provider()
        );
    }

    public SpeakerVoiceAnalysisService(
        ChatService chatService,
        ObjectMapper objectMapper,
        AudiobookWorkflowPromptProvider promptProvider,
        DeterministicAudiobookWorkflowFallbackService fallbackService,
        AudiobookProjectRepository audiobookProjectRepository,
        SpeakerCharacterRepository speakerCharacterRepository,
        String chatbotProvider
    ) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.promptProvider = promptProvider;
        this.fallbackService = fallbackService;
        this.audiobookProjectRepository = audiobookProjectRepository;
        this.speakerCharacterRepository = speakerCharacterRepository;
        this.chatbotProvider = chatbotProvider == null ? "mock" : chatbotProvider.trim().toLowerCase();
    }

    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue) {
        return analyzeInternal(rawDialogue);
    }

    @Transactional
    public SpeakerVoiceAnalysisResponse analyze(String rawDialogue, String projectId) {
        SpeakerVoiceAnalysisResponse response = analyzeInternal(rawDialogue);
        if (projectId != null && !projectId.isBlank()) {
            syncProjectCharacters(projectId, response.speakers());
            return new SpeakerVoiceAnalysisResponse(response.speakers(), projectId, response.projectTitle());
        }
        return response;
    }

    private SpeakerVoiceAnalysisResponse analyzeInternal(String rawDialogue) {
        if (rawDialogue == null || rawDialogue.isBlank()) {
            log.debug("chatbotProvider:" + chatbotProvider);
            return new SpeakerVoiceAnalysisResponse(List.of(), null, fallbackService.suggestProjectTitle(rawDialogue));
        }

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            log.debug("chatbotProvider:" + chatbotProvider);
            return new SpeakerVoiceAnalysisResponse(
                fallbackService.analyzeSpeakers(rawDialogue),
                null,
                fallbackService.suggestProjectTitle(rawDialogue)
            );
        }

        try {
            String answer = chatService.ask(new ChatRequest(promptProvider.getSpeakerVoiceAnalysisPrompt(rawDialogue), null)).answer();
            return parseProviderAnswer(answer, rawDialogue);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AUDIOBOOK_WORKFLOW_PROVIDER_FAILED",
                "The speaker voice analysis provider is currently unavailable. Please try again later.",
                null,
                ex
            );
        }
    }

    private SpeakerVoiceAnalysisResponse parseProviderAnswer(String answer, String rawDialogue) {
        log.debug("start parsing answer:\n " + answer);

        if (answer == null || answer.isBlank()) {
            throw invalidProviderResponse(null);
        }

        try {
            JsonNode root = objectMapper.readTree(AudiobookWorkflowJson.stripMarkdownFence(answer));
            JsonNode speakers = root.path("speakers");
            if (!speakers.isArray()) {
                throw invalidProviderResponse(null);
            }

            List<SpeakerVoiceAnalysisItem> items = new ArrayList<>();
            for (JsonNode speaker : speakers) {
                String speakerName = speaker.path("speakerName").asText("").trim();
                String roleDescription = speaker.path("roleDescription").asText("").trim();
                SpeakerVoice voiceSuggestion = SpeakerVoice.valueOf(speaker.path("voiceSuggestion").asText("").trim().toUpperCase());
                if (!speakerName.isBlank()) {
                    items.add(new SpeakerVoiceAnalysisItem(speakerName, roleDescription, voiceSuggestion));
                }
            }
            if (items.isEmpty()) {
                throw invalidProviderResponse(null);
            }
            String projectTitle = normalizeProjectTitle(root.path("projectTitle").asText(null), rawDialogue);
            return new SpeakerVoiceAnalysisResponse(items, null, projectTitle);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidProviderResponse(ex);
        }
    }

    private String normalizeProjectTitle(String projectTitle, String rawDialogue) {
        if (projectTitle != null) {
            String trimmed = projectTitle.trim();
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return fallbackService.suggestProjectTitle(rawDialogue);
    }

    public List<SpeakerCharacter> syncProjectCharacters(String projectId, List<SpeakerVoiceAnalysisItem> speakers) {
        List<SpeakerVoiceAnalysisItem> safeSpeakers = speakers == null ? List.of() : speakers;
        AudiobookProject project = audiobookProjectRepository.findById(projectId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "AUDIOBOOK_NOT_FOUND",
                "The audiobook project was not found."
            ));

        speakerCharacterRepository.deleteByProjectId(projectId);

        Instant now = Instant.now();
        List<SpeakerCharacter> characters = new ArrayList<>();
        for (int index = 0; index < safeSpeakers.size(); index++) {
            SpeakerVoiceAnalysisItem speaker = safeSpeakers.get(index);
            characters.add(new SpeakerCharacter(
                UUID.randomUUID().toString(),
                projectId,
                index,
                speaker.speakerName(),
                speaker.roleDescription(),
                speaker.voiceSuggestion(),
                now
            ));
        }

        if (!characters.isEmpty()) {
            speakerCharacterRepository.saveAll(characters);
        }

        project.setSpeakerCount(safeSpeakers.size());
        project.setUpdatedAt(now);
        audiobookProjectRepository.save(project);
        return characters;
    }

    private ApiException invalidProviderResponse(Throwable cause) {
        return new ApiException(
            HttpStatus.BAD_GATEWAY,
            "AUDIOBOOK_WORKFLOW_PROVIDER_RESPONSE_INVALID",
            "The speaker voice analysis provider returned an invalid response. Please try again later.",
            null,
            cause
        );
    }
}


