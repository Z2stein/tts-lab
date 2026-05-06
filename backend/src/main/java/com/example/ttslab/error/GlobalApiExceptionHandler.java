package com.example.ttslab.error;

import com.example.ttslab.chat.ChatProviderException;
import com.example.ttslab.chat.ChatRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("API request failed (requestId={}, status={}, code={}): {}", requestId, ex.status().value(), ex.code(), ex.getMessage(), ex);
        return ResponseEntity.status(ex.status())
            .header(REQUEST_ID_HEADER, requestId)
            .body(new ApiErrorResponse(ex.status().value(), ex.code(), ex.userMessage(), ex.details(), requestId));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleValidationError(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("API request validation failed (requestId={}): {}", requestId, ex.getMessage());
        return ResponseEntity.badRequest()
            .header(REQUEST_ID_HEADER, requestId)
            .body(new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "The request is invalid. Please check your input and try again.",
                safeValidationDetails(ex),
                requestId
            ));
    }

    @ExceptionHandler(ChatRateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(ChatRateLimitExceededException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.info("Chat rate limit exceeded (requestId={}, window={}, maxRequests={}, retryAfterSeconds={})",
            requestId, ex.window(), ex.maxRequests(), ex.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(REQUEST_ID_HEADER, requestId)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
            .body(new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "Chat usage limit exceeded. Please try again later.",
                "Retry after " + ex.retryAfterSeconds() + " seconds. Limit: " + ex.maxRequests() + " requests per " + ex.window() + ".",
                requestId
            ));
    }

    @ExceptionHandler(ChatProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleProviderError(ChatProviderException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        Throwable cause = ex.getCause();
        String exceptionClass = cause == null ? ex.getClass().getSimpleName() : cause.getClass().getSimpleName();
        log.warn("Chat provider failed (requestId={}, chatModelMissing={}, exceptionClass={}): {}",
            requestId, ex.chatModelMissing(), exceptionClass, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .header(REQUEST_ID_HEADER, requestId)
            .body(new ApiErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                "CHAT_PROVIDER_UNAVAILABLE",
                "Chat provider is currently unavailable. Please try again later.",
                null,
                requestId
            ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.info("API resource not found (requestId={}, path={})", requestId, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .header(REQUEST_ID_HEADER, requestId)
            .body(new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                "The requested resource was not found.",
                null,
                requestId
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unexpected API error (requestId={}, path={})", requestId, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header(REQUEST_ID_HEADER, requestId)
            .body(new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected server error occurred. Please try again later.",
                null,
                requestId
            ));
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
    }

    private String safeValidationDetails(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            return methodArgumentNotValidException.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(null);
        }
        if (ex instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse(null);
        }
        return null;
    }
}
