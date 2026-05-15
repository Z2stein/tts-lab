package com.example.ttslab.audiobooks.workflow;

public record SpeakerVoiceCatalogItemResponse(
        String id,
        String providerVoiceName,
        String displayName,
        String description,
        String imageUrl,
        String demoMp3Url
) {
    public static SpeakerVoiceCatalogItemResponse from(SpeakerVoice voice) {
        String id = voice.name().toLowerCase();
        return new SpeakerVoiceCatalogItemResponse(
                id,
                voice.getKey(),
                voice.getKey(),
                voice.getStyleDescription(),
                "/assets/voices/" + id + "/avatar.png",
                "/assets/voices/" + id + "/demo.mp3"
        );
    }
}
