package com.example.ttslab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RequestRateLimitPropertiesTest {
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
                "request-limits.enabled=true",
                "request-limits.window=PT12H",
                "request-limits.speech-model-limit=600",
                "request-limits.text-model-multiplier=1",
                "request-limits.unit=WORDS"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                RequestRateLimitProperties props = context.getBean(RequestRateLimitProperties.class);
                assertThat(props.enabled()).isTrue();
                assertThat(props.window()).isEqualTo(Duration.ofHours(12));
                assertThat(props.speechModelLimit()).isEqualTo(600L);
                assertThat(props.textModelMultiplier()).isEqualTo(1L);
                assertThat(props.unit()).isEqualTo(RequestRateLimitUnit.WORDS);
            });
    }

    @Test
    void invalidValuesFailBinding() {
        contextRunner
            .withPropertyValues(
                "request-limits.enabled=true",
                "request-limits.window=PT0S",
                "request-limits.speech-model-limit=0",
                "request-limits.text-model-multiplier=0",
                "request-limits.unit=WORDS"
            )
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RequestRateLimitProperties.class)
    static class TestConfig {
    }
}
