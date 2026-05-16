package com.example.ttslab.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "demo-token")
@Validated
@ValidDemoTokenProperties
public record DemoTokenProperties(
    boolean enabled,
    String signingSecret
) {
}
