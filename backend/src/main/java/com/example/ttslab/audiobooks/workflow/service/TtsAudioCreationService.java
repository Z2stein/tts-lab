package com.example.ttslab.audiobooks.workflow.service;

import com.example.ttslab.error.ApiException;
import com.example.ttslab.config.ChatbotProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @Autowired
    public TtsAudioCreationService(
        ObjectProvider<GoogleTtsClient> googleTtsClientProvider,
        ChatbotProperties chatbotProperties
    ) {
        this(googleTtsClientProvider, chatbotProperties == null ? PROVIDER_MOCK : chatbotProperties.provider());
    }

    public TtsAudioCreationService(
        ObjectProvider<GoogleTtsClient> googleTtsClientProvider,
        String chatbotProvider
    ) {
        this.googleTtsClientProvider = googleTtsClientProvider;
        this.chatbotProvider = chatbotProvider == null ? PROVIDER_MOCK : chatbotProvider.trim().toLowerCase();
    }

    public TtsAudioFile createAudio(SingleSpeakerRenderPlanResponse requestPlan) {
        List<SingleSpeakerRenderRequest> renderRequests = requestPlan == null ? List.of() : requestPlan.renderRequests();
        if (renderRequests == null || renderRequests.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "TTS_AUDIO_RENDER_REQUESTS_REQUIRED",
                "At least one render request is required to create audio.",
                null,
                null
            );
        }

        List<byte[]> audioParts = createAudioParts(renderRequests);
        if (audioParts.size() == 1) {
            return new TtsAudioFile(audioParts.getFirst(), "audio/mpeg", "tts-render-request-1.mp3");
        }
        return new TtsAudioFile(concatenateMp3(audioParts), "audio/mpeg", "tts-render-plan.mp3");
    }

    public List<byte[]> createAudioParts(List<SingleSpeakerRenderRequest> renderRequests) {
        if (renderRequests == null || renderRequests.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "TTS_AUDIO_RENDER_REQUESTS_REQUIRED",
                "At least one render request is required to create audio.",
                null,
                null
            );
        }
        return renderRequests.stream().map(this::createAudioPart).toList();
    }

    private byte[] createAudioPart(SingleSpeakerRenderRequest renderRequest) {
        if (PROVIDER_MOCK.equals(chatbotProvider)) {
            return mockMp3(renderRequest);
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
            return googleTtsClient.synthesize(renderRequest);
        } catch (TtsAudioCreationException ex) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "TTS_AUDIO_PROVIDER_UNAVAILABLE",
                "The text-to-speech provider is currently unavailable. Please try again later.",
                null,
                ex
            );
        }
    }

    private byte[] mockMp3(SingleSpeakerRenderRequest request) {
        String text = request == null || request.input() == null || request.input().get("text") == null
            ? ""
            : request.input().get("text").toString();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(MOCK_MP3_PREFIX);
        output.writeBytes(("TTS-LAB-MOCK-MP3\n" + text).getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private byte[] concatenateMp3(List<byte[]> audioParts) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] audioPart : audioParts) {
            bytes.writeBytes(audioPart);
        }
        return bytes.toByteArray();
    }
}

