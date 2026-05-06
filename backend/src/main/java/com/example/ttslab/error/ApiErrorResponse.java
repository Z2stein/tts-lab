package com.example.ttslab.error;

public record ApiErrorResponse(
    int status,
    String code,
    String message,
    String details,
    String requestId
) {
}
