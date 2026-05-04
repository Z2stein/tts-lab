package com.example.ttslab.auth;

import com.example.ttslab.TtsLabApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthConfigValidationTest {
    @Test
    @DisplayName("GIVEN main environment WHEN mock mode THEN startup fails")
    void mockOnMainFails() {
        assertThrows(Exception.class, () -> run("AUTH_MODE=mock", "ENVIRONMENT=main"));
    }

    @Test
    @DisplayName("GIVEN google mode WHEN google credentials missing THEN startup fails")
    void googleMissingCredentialsFails() {
        assertThrows(Exception.class, () -> run("AUTH_MODE=google", "ENVIRONMENT=dev"));
    }

    @Test
    @DisplayName("GIVEN mock mode WHEN feature environment THEN startup succeeds")
    void mockFeatureStarts() {
        try (ConfigurableApplicationContext ignored = run("AUTH_MODE=mock", "ENVIRONMENT=feature")) {}
    }

    private ConfigurableApplicationContext run(String... props) {
        return new SpringApplicationBuilder(TtsLabApplication.class).properties(props).run();
    }
}
