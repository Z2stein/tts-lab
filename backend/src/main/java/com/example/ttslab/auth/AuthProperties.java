package com.example.ttslab.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthProperties {
    @Value("${AUTH_MODE:mock}")
    private String authMode;
    @Value("${ENVIRONMENT:feature}")
    private String environment;
    @Value("${APP_BASE_URL:http://localhost:8080}")
    private String appBaseUrl;
    @Value("${GOOGLE_CLIENT_ID:}")
    private String googleClientId;
    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String googleClientSecret;
    @Value("${MOCK_USER_ID:mock-user-1}")
    private String mockUserId;
    @Value("${MOCK_USER_EMAIL:mock.user@example.com}")
    private String mockUserEmail;
    @Value("${MOCK_USER_NAME:Mock User}")
    private String mockUserName;
    @Value("${MOCK_USER_ROLES:USER}")
    private String mockUserRoles;

    public AuthMode mode() { return AuthMode.from(authMode); }
    public String environment() { return environment.toLowerCase(); }
    public String appBaseUrl() { return appBaseUrl; }
    public String googleClientId() { return googleClientId; }
    public String googleClientSecret() { return googleClientSecret; }
    public CurrentUser mockCurrentUser() {
        List<String> roles = List.of(mockUserRoles.split(","));
        return new CurrentUser(mockUserId, mockUserEmail, mockUserName, roles, "mock");
    }
}
