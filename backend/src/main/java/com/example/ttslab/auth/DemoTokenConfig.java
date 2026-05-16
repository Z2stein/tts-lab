package com.example.ttslab.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoTokenProperties.class)
public class DemoTokenConfig {
}
