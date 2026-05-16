package com.example.ttslab.auth;

public class InvalidDemoTokenException extends RuntimeException {
    public InvalidDemoTokenException(String message) {
        super(message);
    }

    public InvalidDemoTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
