package com.example.ttslab.audiobooks.workflow;

public class TtsAudioCreationException extends RuntimeException {
    private boolean configurationError;

    public TtsAudioCreationException(String message, Throwable cause, boolean configurationError) {
        super(message, cause);
        this.configurationError = configurationError;
    }

    public TtsAudioCreationException(String message) {
        super(message);
    }

    public boolean configurationError() {
        return configurationError;
    }
}

