package com.example.ttslab.chat;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatRateLimitProperties.class)
public class ChatRateLimitConfig {

    @Bean
    ChatUsageCounterStore chatUsageCounterStore() {
        return new InMemoryChatUsageCounterStore();
    }

    @Bean
    ChatUsageWindowCalculator chatUsageWindowCalculator() {
        return new ChatUsageWindowCalculator();
    }

    @Bean
    Clock chatRateLimitClock() {
        return Clock.systemUTC();
    }
}
