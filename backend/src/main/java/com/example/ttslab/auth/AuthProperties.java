package com.example.ttslab.auth;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "auth")
@Validated
@ValidAuthProperties
public record AuthProperties(
    @NotNull
    AuthMode mode,
    @NotBlank
    String environment,
    @NotBlank
    String appBaseUrl,
    String googleClientId,
    String googleClientSecret,
    @NotBlank
    String mockUserId,
    @NotBlank
    String mockUserEmail,
    @NotBlank
    String mockUserName,
    @NotBlank
    String mockUserRoles
) {
    public CurrentUser mockCurrentUser() {
        List<String> roles = List.of(mockUserRoles.split(","));
        return new CurrentUser(mockUserId, mockUserEmail, mockUserName, roles, "mock");
    }
}
