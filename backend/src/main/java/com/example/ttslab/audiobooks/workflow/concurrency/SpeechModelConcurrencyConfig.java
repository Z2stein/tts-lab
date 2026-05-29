package com.example.ttslab.audiobooks.workflow.concurrency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SpeechModelConcurrencyProperties.class)
public class SpeechModelConcurrencyConfig {
}
