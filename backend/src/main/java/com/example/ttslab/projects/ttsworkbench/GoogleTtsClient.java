package com.example.ttslab.projects.ttsworkbench;

public interface GoogleTtsClient {
    byte[] synthesize(SingleSpeakerRenderRequest request);
}
