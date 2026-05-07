package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TtsWorkbenchTtsProviderStartupTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(GoogleCloudTtsClient.class);

    @Test
    void mockModeStartsWithoutGoogleTtsCredentials() {
        contextRunner
            .withPropertyValues("chatbot.provider=mock")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(GoogleCloudTtsClient.class);
            });
    }

    @Test
    void geminiModeFailsClearlyWithoutGoogleTtsCredentials() {
        contextRunner
            .withPropertyValues("chatbot.provider=gemini", "tts-workbench.google.service-account-json-b64=")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasCauseInstanceOf(ApiException.class);
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("Google Cloud Text-to-Speech credentials are missing or invalid for gemini mode.");
            });
    }
}
