package com.example.ttslab.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ChatRateLimitPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class
        ))
        .withUserConfiguration(TestConfig.class);

    @Test
    void validValuesBindExactly() {
        contextRunner
            .withPropertyValues(
                "chat-limit.enabled=true",
                "chat-limit.window=PT1H",
                "chat-limit.max-requests=5",
                "chat-limit.id-header=X-User-Id"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                ChatRateLimitProperties props = context.getBean(ChatRateLimitProperties.class);
                assertThat(props.enabled()).isTrue();
                assertThat(props.window()).isEqualTo(Duration.ofHours(1));
                assertThat(props.maxRequests()).isEqualTo(5);
                assertThat(props.idHeader()).isEqualTo("X-User-Id");
            });
    }

    @Test
    void invalidValuesFailBinding() {
        contextRunner
            .withPropertyValues(
                "chat-limit.enabled=true",
                "chat-limit.window=PT0S",
                "chat-limit.max-requests=0",
                "chat-limit.id-header="
            )
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChatRateLimitProperties.class)
    static class TestConfig {
    }
}
