package com.example.ttslab.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String userMessage;
    private final String details;

    public ApiException(HttpStatus status, String code, String userMessage) {
        this(status, code, userMessage, null, null);
    }

    public ApiException(HttpStatus status, String code, String userMessage, String details, Throwable cause) {
        super(userMessage, cause);
        this.status = status;
        this.code = code;
        this.userMessage = userMessage;
        this.details = details;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String userMessage() {
        return userMessage;
    }

    public String details() {
        return details;
    }
}
