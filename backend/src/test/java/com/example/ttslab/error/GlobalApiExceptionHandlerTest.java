package com.example.ttslab.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.ttslab.chat.ChatProviderException;
import com.example.ttslab.chat.ChatRateLimitExceededException;
import com.example.ttslab.prompts.ModelType;
import com.example.ttslab.ratelimit.RequestRateLimitExceededException;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitUnit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("GlobalApiExceptionHandler")
class GlobalApiExceptionHandlerTest {

    private GlobalApiExceptionHandler handler;
    private HttpServletRequest mockRequest;

    @BeforeEach
    void setup() {
        handler = new GlobalApiExceptionHandler();
        mockRequest = mock(HttpServletRequest.class);
    }

    // ============ REQUEST ID GENERATION TESTS ============

    @Test
    @DisplayName("request ID from header is used when present")
    void requestIdFromHeader() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-12345");

        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "TEST", "Test error");
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, mockRequest);

        assertThat(response.getBody().requestId()).isEqualTo("req-12345");
    }

    @Test
    @DisplayName("request ID is generated when header is missing")
    void requestIdGenerated() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn(null);

        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "TEST", "Test error");
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, mockRequest);

        // Should be a valid UUID
        String requestId = response.getBody().requestId();
        assertThat(UUID.fromString(requestId)).isNotNull();
    }

    @Test
    @DisplayName("request ID header is trimmed when present")
    void requestIdTrimmed() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("  req-456  ");

        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "TEST", "Test");
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, mockRequest);

        assertThat(response.getBody().requestId()).isEqualTo("req-456");
    }

    @Test
    @DisplayName("request ID is generated when header is blank")
    void requestIdGeneratedForBlankHeader() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("   ");

        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "TEST", "Test");
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, mockRequest);

        String requestId = response.getBody().requestId();
        assertThat(UUID.fromString(requestId)).isNotNull();
    }

    // ============ API EXCEPTION HANDLER TESTS ============

    @Test
    @DisplayName("handleApiException returns correct status and code")
    void handleApiExceptionStatus() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-test");

        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("handleApiException includes request ID header")
    void handleApiExceptionIncludesRequestIdHeader() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-header-test");

        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "ERROR", "Error");
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, mockRequest);

        assertThat(response.getHeaders().get("X-Request-Id")).containsExactly("req-header-test");
    }

    @Test
    @DisplayName("handleApiException includes details when provided")
    void handleApiExceptionWithDetails() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-1");

        ApiException ex = new ApiException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Validation failed",
            "Field 'email' is required",
            null
        );
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, mockRequest);

        assertThat(response.getBody().details()).isEqualTo("Field 'email' is required");
    }

    // ============ VALIDATION ERROR HANDLER TESTS ============

    @Test
    @DisplayName("handleValidationError handles MethodArgumentNotValidException")
    void handleValidationErrorMethodArgumentNotValid() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-validation");

        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "must be valid");
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationError(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().details()).contains("email");
    }

    @Test
    @DisplayName("handleValidationError handles ConstraintViolationException")
    void handleValidationErrorConstraintViolation() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-constraint");

        ConstraintViolationException ex = mock(ConstraintViolationException.class);
        when(ex.getConstraintViolations()).thenReturn(new HashSet<>());

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationError(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("handleValidationError includes request ID")
    void handleValidationErrorIncludesRequestId() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-validation-id");

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(mock(BindingResult.class));
        when(ex.getBindingResult().getFieldErrors()).thenReturn(java.util.List.of());

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationError(ex, mockRequest);

        assertThat(response.getHeaders().get("X-Request-Id")).containsExactly("req-validation-id");
    }

    // ============ RATE LIMIT EXCEPTION HANDLER TESTS ============

    @Test
    @DisplayName("handleRateLimitExceeded returns 429 status")
    void handleChatRateLimitExceeded() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-ratelimit");

        ChatRateLimitExceededException ex = new ChatRateLimitExceededException(
            30, "5m", 10
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleRateLimitExceeded(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().status()).isEqualTo(429);
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("handleRateLimitExceeded includes Retry-After header")
    void handleRateLimitExceededIncludesRetryAfter() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-rate");

        ChatRateLimitExceededException ex = new ChatRateLimitExceededException(
            60, "5m", 10
        );

        ResponseEntity<ApiErrorResponse> response = handler.handleRateLimitExceeded(ex, mockRequest);

        assertThat(response.getHeaders().get("Retry-After")).containsExactly("60");
    }

    @Test
    @DisplayName("handleRequestRateLimitExceeded returns 429 status")
    void handleRequestRateLimitExceeded() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-request-limit");

        RequestRateLimitResult result = new RequestRateLimitResult(
            ModelType.TEXT_MODEL, false, 95, 100, 5, 10, 45, 12345L, RequestRateLimitUnit.WORDS
        );
        RequestRateLimitExceededException ex = new RequestRateLimitExceededException(result);

        ResponseEntity<ApiErrorResponse> response = handler.handleRequestRateLimitExceeded(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().code()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("handleRequestRateLimitExceeded includes Retry-After header")
    void handleRequestRateLimitExceededIncludesRetryAfter() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-request-limit");

        RequestRateLimitResult result = new RequestRateLimitResult(
            ModelType.SPEECH_MODEL, false, 40, 50, 10, 15, 120, 12345L, RequestRateLimitUnit.TOKENS
        );
        RequestRateLimitExceededException ex = new RequestRateLimitExceededException(result);

        ResponseEntity<ApiErrorResponse> response = handler.handleRequestRateLimitExceeded(ex, mockRequest);

        assertThat(response.getHeaders().get("Retry-After")).containsExactly("120");
    }

    // ============ PROVIDER ERROR HANDLER TESTS ============

    @Test
    @DisplayName("handleProviderError returns 502 status")
    void handleProviderError() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-provider");

        ChatProviderException ex = new ChatProviderException("Provider unavailable", null, true);

        ResponseEntity<ApiErrorResponse> response = handler.handleProviderError(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().code()).isEqualTo("CHAT_PROVIDER_UNAVAILABLE");
    }

    @Test
    @DisplayName("handleProviderError includes request ID")
    void handleProviderErrorIncludesRequestId() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-provider-id");

        ChatProviderException ex = new ChatProviderException("Error", null, false);

        ResponseEntity<ApiErrorResponse> response = handler.handleProviderError(ex, mockRequest);

        assertThat(response.getHeaders().get("X-Request-Id")).containsExactly("req-provider-id");
    }

    @Test
    @DisplayName("handleProviderError with cause exception")
    void handleProviderErrorWithCause() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-provider-cause");

        Throwable cause = new RuntimeException("Underlying error");
        ChatProviderException ex = new ChatProviderException("Wrapped error", cause, false);

        ResponseEntity<ApiErrorResponse> response = handler.handleProviderError(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().code()).isEqualTo("CHAT_PROVIDER_UNAVAILABLE");
    }

    // ============ NOT FOUND HANDLER TESTS ============

    @Test
    @DisplayName("handleNotFound returns 404 status")
    void handleNotFound() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-notfound");
        when(mockRequest.getRequestURI()).thenReturn("/api/nonexistent");

        NoResourceFoundException ex = mock(NoResourceFoundException.class);

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("handleNotFound includes request ID")
    void handleNotFoundIncludesRequestId() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-notfound-id");
        when(mockRequest.getRequestURI()).thenReturn("/api/notfound");

        NoResourceFoundException ex = mock(NoResourceFoundException.class);

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(ex, mockRequest);

        assertThat(response.getHeaders().get("X-Request-Id")).containsExactly("req-notfound-id");
    }

    // ============ UNEXPECTED ERROR HANDLER TESTS ============

    @Test
    @DisplayName("handleUnexpectedError returns 500 status")
    void handleUnexpectedError() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-unexpected");
        when(mockRequest.getRequestURI()).thenReturn("/api/test");

        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedError(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("handleUnexpectedError includes request ID")
    void handleUnexpectedErrorIncludesRequestId() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-unexpected-id");
        when(mockRequest.getRequestURI()).thenReturn("/api/error");

        Exception ex = new Exception("Generic error");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedError(ex, mockRequest);

        assertThat(response.getHeaders().get("X-Request-Id")).containsExactly("req-unexpected-id");
    }

    @Test
    @DisplayName("handleUnexpectedError with null cause")
    void handleUnexpectedErrorNullCause() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("req-null-cause");
        when(mockRequest.getRequestURI()).thenReturn("/api/test");

        Exception ex = new Exception("Error without cause");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpectedError(ex, mockRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ============ MULTIPLE EXCEPTION TYPES ============

    @Test
    @DisplayName("handler processes different exception types correctly")
    void multipleExceptionTypes() {
        when(mockRequest.getHeader("X-Request-Id")).thenReturn(null);
        when(mockRequest.getRequestURI()).thenReturn("/api/test");

        // Test that each handler returns appropriate status
        ApiException apiEx = new ApiException(HttpStatus.BAD_REQUEST, "TEST", "test");
        assertThat(handler.handleApiException(apiEx, mockRequest).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        NoResourceFoundException nfEx = mock(NoResourceFoundException.class);
        assertThat(handler.handleNotFound(nfEx, mockRequest).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Exception genEx = new Exception("test");
        assertThat(handler.handleUnexpectedError(genEx, mockRequest).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
