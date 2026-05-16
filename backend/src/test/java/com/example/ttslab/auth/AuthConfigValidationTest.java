package com.example.ttslab.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthConfigValidationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class
        ))
        .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("GIVEN main environment WHEN mock mode THEN startup fails")
    void mockOnMainFails() {
        contextRunner
            .withPropertyValues(
                "auth.mode=mock",
                "auth.environment=main",
                "auth.app-base-url=http://localhost:8080",
                "auth.mock-user-id=mock-user-1",
                "auth.mock-user-email=mock.user@example.com",
                "auth.mock-user-name=Mock User",
                "auth.mock-user-roles=USER"
            )
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("GIVEN google mode WHEN google credentials missing THEN startup fails")
    void googleMissingCredentialsFails() {
        contextRunner
            .withPropertyValues(
                "auth.mode=google",
                "auth.environment=dev",
                "auth.app-base-url=http://localhost:8080",
                "auth.mock-user-id=mock-user-1",
                "auth.mock-user-email=mock.user@example.com",
                "auth.mock-user-name=Mock User",
                "auth.mock-user-roles=USER",
                "auth.google-client-id=",
                "auth.google-client-secret="
            )
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("GIVEN mock mode WHEN feature environment THEN startup succeeds")
    void mockFeatureStarts() {
        contextRunner
            .withPropertyValues(
                "auth.mode=mock",
                "auth.environment=feature",
                "auth.app-base-url=http://localhost:8080",
                "auth.mock-user-id=mock-user-1",
                "auth.mock-user-email=mock.user@example.com",
                "auth.mock-user-name=Mock User",
                "auth.mock-user-roles=USER"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(AuthProperties.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthProperties.class)
    static class TestConfig {
    }
}
