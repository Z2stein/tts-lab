package com.example.ttslab.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RequestRateLimitProperties.class)
public class RequestRateLimitConfig {
}

