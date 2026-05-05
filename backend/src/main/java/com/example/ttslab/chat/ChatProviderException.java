package com.example.ttslab.chat;

public class ChatProviderException extends RuntimeException {
    private final boolean chatModelMissing;

    public ChatProviderException(String message, Throwable cause, boolean chatModelMissing) {
        super(message, cause);
        this.chatModelMissing = chatModelMissing;
    }

    public boolean chatModelMissing() {
        return chatModelMissing;
    }
}
