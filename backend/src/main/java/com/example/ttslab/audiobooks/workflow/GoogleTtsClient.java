package com.example.ttslab.audiobooks.workflow;

import com.example.ttslab.audiobooks.model.AudiobookProject;

public interface GoogleTtsClient {
    byte[] synthesize(AudiobookProject request, int targetSegmentIndex);
}

