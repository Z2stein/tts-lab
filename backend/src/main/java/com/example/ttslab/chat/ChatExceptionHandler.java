package com.example.ttslab.chat;

import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ChatExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationError() {
        return Map.of("error", "Invalid chat request.");
    }


    @ExceptionHandler(ChatRateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> handleRateLimitExceeded(ChatRateLimitExceededException ex) {
        Map<String, Object> body = Map.of(
            "error", "RATE_LIMIT_EXCEEDED",
            "message", "Chat usage limit exceeded. Please try again later.",
            "retry_after", ex.retryAfterSeconds(),
            "limit", Map.of("window", ex.window(), "max_requests", ex.maxRequests())
        );
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
            .body(body);
    }

    @ExceptionHandler(ChatProviderException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleProviderError(ChatProviderException ex) {
        Throwable cause = ex.getCause();
        String exceptionClass = cause == null ? ex.getClass().getSimpleName() : cause.getClass().getSimpleName();
        String exceptionMessage = cause == null ? ex.getMessage() : cause.getMessage();

        log.warn("Chatbot provider failed (chatModelMissing={}, exceptionClass={}, exceptionMessage={})",
            ex.chatModelMissing(), exceptionClass, exceptionMessage);
        return Map.of("error", "Chat provider is currently unavailable.");
    }
}
