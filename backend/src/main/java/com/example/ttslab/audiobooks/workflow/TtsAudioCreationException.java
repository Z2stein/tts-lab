package com.example.ttslab.audiobooks.workflow;

public class TtsAudioCreationException extends RuntimeException {
    public enum Kind {
        PROVIDER,
        CONFIGURATION,
        INPUT_TOO_LARGE
    }

    private final Kind kind;

    public TtsAudioCreationException(String message, Throwable cause, Kind kind) {
        super(message, cause);
        this.kind = kind == null ? Kind.PROVIDER : kind;
    }

    public TtsAudioCreationException(String message) {
        this(message, null, Kind.PROVIDER);
    }

    public Kind kind() {
        return kind;
    }

    public boolean configurationError() {
        return kind == Kind.CONFIGURATION;
    }

    public boolean inputTooLarge() {
        return kind == Kind.INPUT_TOO_LARGE;
    }
}
