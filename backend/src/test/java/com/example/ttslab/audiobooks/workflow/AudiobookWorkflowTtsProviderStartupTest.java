package com.example.ttslab.audiobooks.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AudiobookWorkflowTtsProviderStartupTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(GoogleCloudTtsClient.class);

    @Test
    void mockModeStartsWithoutGoogleTtsCredentials() {
        contextRunner
            .withPropertyValues("chatbot.provider=mock")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GoogleCloudTtsClient.class);
            });
    }

    @Test
    void geminiModeStartsWithoutGoogleTtsCredentials() {
        contextRunner
            .withPropertyValues("chatbot.provider=gemini", "audiobook-workflow.google.service-account-json-b64=")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GoogleCloudTtsClient.class);
            });
    }
}


