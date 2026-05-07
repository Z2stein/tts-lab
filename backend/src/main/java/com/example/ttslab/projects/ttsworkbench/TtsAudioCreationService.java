package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.error.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TtsAudioCreationService {
    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_MOCK = "mock";
    private static final byte[] MOCK_MP3_PREFIX = new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, 0, 0};

    private final ObjectProvider<GoogleTtsClient> googleTtsClientProvider;
    private final String chatbotProvider;

    public TtsAudioCreationService(
        ObjectProvider<GoogleTtsClient> googleTtsClientProvider,
        @Value("${chatbot.provider:mock}") String chatbotProvider
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

        List<byte[]> audioParts = renderRequests.stream().map(this::createAudioPart).toList();
        if (audioParts.size() == 1) {
            return new TtsAudioFile(audioParts.getFirst(), "audio/mpeg", "tts-workbench-audio.mp3");
        }
        return new TtsAudioFile(zip(audioParts), "application/zip", "tts-workbench-audio.zip");
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

    private byte[] zip(List<byte[]> audioParts) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (int index = 0; index < audioParts.size(); index++) {
                    zip.putNextEntry(new ZipEntry("render-request-" + (index + 1) + ".mp3"));
                    zip.write(audioParts.get(index));
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "TTS_AUDIO_ARCHIVE_FAILED",
                "Audio was created, but packaging it for download failed. Please try again later.",
                null,
                ex
            );
        }
    }
}
