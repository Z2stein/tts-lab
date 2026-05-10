package com.example.ttslab.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiErrorResponse")
class ApiErrorResponseTest {

    @Test
    @DisplayName("record constructor creates response with all fields")
    void constructorCreatesResponse() {
        ApiErrorResponse response = new ApiErrorResponse(400, "BAD_REQUEST", "Invalid input", "Field missing", "req-123");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("BAD_REQUEST");
        assertThat(response.message()).isEqualTo("Invalid input");
        assertThat(response.details()).isEqualTo("Field missing");
        assertThat(response.requestId()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("record allows null details")
    void nullDetails() {
        ApiErrorResponse response = new ApiErrorResponse(404, "NOT_FOUND", "Not found", null, "req-456");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.code()).isEqualTo("NOT_FOUND");
        assertThat(response.message()).isEqualTo("Not found");
        assertThat(response.details()).isNull();
        assertThat(response.requestId()).isEqualTo("req-456");
    }

    @Test
    @DisplayName("record with HTTP 200 status")
    void okStatus() {
        ApiErrorResponse response = new ApiErrorResponse(200, "OK", "Request successful", null, "req-ok");
        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("record with various error statuses")
    void variousErrorStatuses() {
        assertThat(new ApiErrorResponse(400, "ERR", "msg", null, "id").status()).isEqualTo(400);
        assertThat(new ApiErrorResponse(401, "ERR", "msg", null, "id").status()).isEqualTo(401);
        assertThat(new ApiErrorResponse(403, "ERR", "msg", null, "id").status()).isEqualTo(403);
        assertThat(new ApiErrorResponse(404, "ERR", "msg", null, "id").status()).isEqualTo(404);
        assertThat(new ApiErrorResponse(409, "ERR", "msg", null, "id").status()).isEqualTo(409);
        assertThat(new ApiErrorResponse(429, "ERR", "msg", null, "id").status()).isEqualTo(429);
        assertThat(new ApiErrorResponse(500, "ERR", "msg", null, "id").status()).isEqualTo(500);
        assertThat(new ApiErrorResponse(502, "ERR", "msg", null, "id").status()).isEqualTo(502);
        assertThat(new ApiErrorResponse(503, "ERR", "msg", null, "id").status()).isEqualTo(503);
    }

    @Test
    @DisplayName("record with empty strings")
    void emptyStrings() {
        ApiErrorResponse response = new ApiErrorResponse(400, "", "", "", "");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEmpty();
        assertThat(response.message()).isEmpty();
        assertThat(response.details()).isEmpty();
        assertThat(response.requestId()).isEmpty();
    }

    @Test
    @DisplayName("record with long error message")
    void longErrorMessage() {
        String longMessage = "A".repeat(1000);
        ApiErrorResponse response = new ApiErrorResponse(400, "ERROR", longMessage, null, "req-id");

        assertThat(response.message()).isEqualTo(longMessage);
    }

    @Test
    @DisplayName("record with special characters in fields")
    void specialCharacters() {
        String code = "ERROR_<>&\"";
        String message = "Invalid: \"input\" & <special>";
        String details = "Path: \\Windows\\System32";

        ApiErrorResponse response = new ApiErrorResponse(400, code, message, details, "req-123");

        assertThat(response.code()).isEqualTo(code);
        assertThat(response.message()).isEqualTo(message);
        assertThat(response.details()).isEqualTo(details);
    }

    @Test
    @DisplayName("record with UUID request ID")
    void uuidRequestId() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        ApiErrorResponse response = new ApiErrorResponse(500, "ERROR", "msg", null, uuid);

        assertThat(response.requestId()).isEqualTo(uuid);
    }

    @Test
    @DisplayName("record field access")
    void fieldAccess() {
        ApiErrorResponse response = new ApiErrorResponse(400, "VALIDATION_FAILED", "Validation error", "Email invalid", "req-789");

        // Access fields via method references
        assertThat(response)
            .extracting(
                ApiErrorResponse::status,
                ApiErrorResponse::code,
                ApiErrorResponse::message,
                ApiErrorResponse::details,
                ApiErrorResponse::requestId
            )
            .containsExactly(400, "VALIDATION_FAILED", "Validation error", "Email invalid", "req-789");
    }

    @Test
    @DisplayName("record with zero status code")
    void zeroStatus() {
        ApiErrorResponse response = new ApiErrorResponse(0, "UNKNOWN", "Unknown error", null, "id");
        assertThat(response.status()).isEqualTo(0);
    }

    @Test
    @DisplayName("record with negative status code")
    void negativeStatus() {
        ApiErrorResponse response = new ApiErrorResponse(-1, "ERROR", "Error", null, "id");
        assertThat(response.status()).isEqualTo(-1);
    }
}
