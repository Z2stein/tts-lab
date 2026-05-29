package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.audiobooks.model.AudiobookProject;
import com.example.ttslab.audiobooks.workflow.concurrency.SpeechModelConcurrencyLimiter;
import com.example.ttslab.error.ApiException;
import com.example.ttslab.config.ChatbotProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.example.ttslab.audiobooks.workflow.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TtsAudioCreationService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_MOCK = "mock";
    private static final byte[] MOCK_MP3_PREFIX = new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, 0, 0};

    private final ObjectProvider<GoogleTtsClient> googleTtsClientProvider;
    private final String chatbotProvider;
    private final SpeechModelConcurrencyLimiter concurrencyLimiter;

    @Autowired
    public TtsAudioCreationService(
        ObjectProvider<GoogleTtsClient> googleTtsClientProvider,
        ChatbotProperties chatbotProperties,
        SpeechModelConcurrencyLimiter concurrencyLimiter
    ) {
        this(googleTtsClientProvider, chatbotProperties == null ? PROVIDER_MOCK : chatbotProperties.provider(), concurrencyLimiter);
    }

    public TtsAudioCreationService(
        ObjectProvider<GoogleTtsClient> googleTtsClientProvider,
        String chatbotProvider,
        SpeechModelConcurrencyLimiter concurrencyLimiter
    ) {
        this.googleTtsClientProvider = googleTtsClientProvider;
        this.chatbotProvider = chatbotProvider == null ? PROVIDER_MOCK : chatbotProvider.trim().toLowerCase();
        this.concurrencyLimiter = concurrencyLimiter;
    }

    public TtsAudioFile createAudio(AudiobookProject project, int targetSegmentIndex) {

        byte[] audioPart = createAudioPart(project,targetSegmentIndex);

        return new TtsAudioFile(concatenateMp3(audioPart), "audio/mpeg", "tts-render-plan.mp3");
    }
    private byte[] createAudioPart(AudiobookProject project, int targetSegmentIndex) {
        if (PROVIDER_MOCK.equals(chatbotProvider)) {
            return mockMp3(project, targetSegmentIndex);
        }

        if (!PROVIDER_GEMINI.equals(chatbotProvider)) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TTS_AUDIO_PROVIDER_UNAVAILABLE",
                "The text-to-speech provider is currently unavailable. Please try again later.",
                null,
                null
            );
        }

        GoogleTtsClient googleTtsClient = googleTtsClientProvider.getIfAvailable();
        if (googleTtsClient == null) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TTS_AUDIO_PROVIDER_UNAVAILABLE",
                "The text-to-speech provider is currently unavailable. Please try again later.",
                null,
                null
            );
        }

        try {
            return concurrencyLimiter.callWithPermit(() -> googleTtsClient.synthesize(project, targetSegmentIndex));
        } catch (TtsAudioCreationException ex) {
            if (ex.inputTooLarge()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TTS_INPUT_TOO_LARGE",
                    "The text is too long for the speech provider. Please shorten the segment and try again.",
                    null,
                    ex
                );
            }
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TTS_AUDIO_PROVIDER_UNAVAILABLE",
                "The text-to-speech provider is currently unavailable. Please try again later.",
                null,
                ex
            );
        }
    }

    private byte[] mockMp3(AudiobookProject project, int targetSegmentIndex) {
        String text = project.getSpeechSegments().get(targetSegmentIndex).getStyledText();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(MOCK_MP3_PREFIX);
        output.writeBytes(("TTS-LAB-MOCK-MP3\n" + text).getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private byte[] concatenateMp3(byte[] ... audioParts) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] audioPart : audioParts) {
            bytes.writeBytes(audioPart);
        }
        return bytes.toByteArray();
    }
}

