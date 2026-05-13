package com.example.ttslab.audiobooks.workflow;

public class TtsAudioCreationException extends RuntimeException {
    private final boolean configurationError;

    public TtsAudioCreationException(String message, Throwable cause, boolean configurationError) {
        super(message, cause);
        this.configurationError = configurationError;
    }

    public boolean configurationError() {
        return configurationError;
    }
}

