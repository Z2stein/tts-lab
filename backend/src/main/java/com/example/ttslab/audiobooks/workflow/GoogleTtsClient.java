package com.example.ttslab.audiobooks.workflow;

public interface GoogleTtsClient {
    byte[] synthesize(SingleSpeakerRenderRequest request);
}

