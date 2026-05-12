package com.example.ttslab.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
    AuthMode mode,
    String environment,
    String appBaseUrl,
    String googleClientId,
    String googleClientSecret,
    String mockUserId,
    String mockUserEmail,
    String mockUserName,
    String mockUserRoles
) {
    public AuthProperties {
        mode = mode == null ? AuthMode.MOCK : mode;
        environment = normalizeEnvironment(environment);
        appBaseUrl = normalize(appBaseUrl, "http://localhost:8080");
        googleClientId = normalize(googleClientId, "");
        googleClientSecret = normalize(googleClientSecret, "");
        mockUserId = normalize(mockUserId, "mock-user-1");
        mockUserEmail = normalize(mockUserEmail, "mock.user@example.com");
        mockUserName = normalize(mockUserName, "Mock User");
        mockUserRoles = normalize(mockUserRoles, "USER");
    }

    public CurrentUser mockCurrentUser() {
        List<String> roles = List.of(mockUserRoles.split(","));
        return new CurrentUser(mockUserId, mockUserEmail, mockUserName, roles, "mock");
    }

    private static String normalizeEnvironment(String value) {
        String normalized = normalize(value, "feature");
        return normalized.toLowerCase();
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
