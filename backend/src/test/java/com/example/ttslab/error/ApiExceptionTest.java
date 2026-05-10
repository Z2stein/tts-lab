package com.example.ttslab.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("ApiException")
class ApiExceptionTest {

    @Test
    @DisplayName("constructor with status, code, and userMessage creates exception")
    void constructorWithThreeArgs() {
        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Input is invalid");

        assertThat(ex)
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Input is invalid");
        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.code()).isEqualTo("INVALID_INPUT");
        assertThat(ex.userMessage()).isEqualTo("Input is invalid");
        assertThat(ex.details()).isNull();
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("constructor with all parameters creates exception with details and cause")
    void constructorWithAllArgs() {
        Throwable cause = new IllegalArgumentException("Original error");
        ApiException ex = new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "SERVER_ERROR",
            "Server error occurred",
            "Database connection failed",
            cause
        );

        assertThat(ex.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.code()).isEqualTo("SERVER_ERROR");
        assertThat(ex.userMessage()).isEqualTo("Server error occurred");
        assertThat(ex.details()).isEqualTo("Database connection failed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("status() returns correct HTTP status")
    void statusGetter() {
        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
        assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("code() returns correct error code")
    void codeGetter() {
        ApiException ex = new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied");
        assertThat(ex.code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("userMessage() returns correct user message")
    void userMessageGetter() {
        String message = "You don't have permission to access this resource";
        ApiException ex = new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
        assertThat(ex.userMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("details() returns null when not provided")
    void detailsGetterNull() {
        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid input");
        assertThat(ex.details()).isNull();
    }

    @Test
    @DisplayName("details() returns provided details")
    void detailsGetterWithValue() {
        String details = "Field 'email' must be a valid email address";
        ApiException ex = new ApiException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Invalid input",
            details,
            null
        );
        assertThat(ex.details()).isEqualTo(details);
    }

    @Test
    @DisplayName("exception can be thrown and caught")
    void exceptionThrowableAndCatchable() {
        try {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEST_ERROR", "Test error message");
        } catch (ApiException ex) {
            assertThat(ex.code()).isEqualTo("TEST_ERROR");
            assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("exception with various HTTP statuses")
    void exceptionWithVariousStatuses() {
        assertThat(new ApiException(HttpStatus.BAD_REQUEST, "ERR", "msg").status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new ApiException(HttpStatus.UNAUTHORIZED, "ERR", "msg").status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(new ApiException(HttpStatus.FORBIDDEN, "ERR", "msg").status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(new ApiException(HttpStatus.NOT_FOUND, "ERR", "msg").status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new ApiException(HttpStatus.CONFLICT, "ERR", "msg").status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ERR", "msg").status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ERR", "msg").status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("exception preserves cause chain")
    void causeChainsCorrectly() {
        Throwable original = new RuntimeException("Original exception");
        Throwable wrapped = new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WRAPPED", "Wrapped error", null, original);

        assertThat(wrapped.getCause()).isEqualTo(original);
    }

    @Test
    @DisplayName("exception with empty details string")
    void emptyDetailsString() {
        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "ERR", "msg", "", null);
        assertThat(ex.details()).isEmpty();
    }

    @Test
    @DisplayName("exception with empty code string")
    void emptyCodeString() {
        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "", "msg");
        assertThat(ex.code()).isEmpty();
    }

    @Test
    @DisplayName("exception with special characters in message")
    void specialCharactersInMessage() {
        String message = "Invalid input: \"test\" & <special> chars \\ /";
        ApiException ex = new ApiException(HttpStatus.BAD_REQUEST, "INVALID", message);
        assertThat(ex.userMessage()).isEqualTo(message);
    }
}
