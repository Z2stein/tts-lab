package com.example.ttslab.auth;

public enum AuthMode {
    GOOGLE,
    MOCK;

    public static AuthMode from(String raw) {
        return AuthMode.valueOf(raw.trim().toUpperCase());
    }
}
