package com.example.ttslab.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ChatRateLimitPropertiesTest {

    @Test
    void defaultsAppliedForInvalidValues() {
        ChatRateLimitProperties props = new ChatRateLimitProperties(true, null, 0, " ");
        assertTrue(props.enabled());
        assertEquals(Duration.ofHours(1), props.window());
        assertEquals(100, props.maxRequests());
        assertEquals("X-User-Id", props.idHeader());
    }
}
