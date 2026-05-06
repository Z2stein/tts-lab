package com.example.ttslab.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InMemoryChatUsageCounterStoreTest {

    @Test
    void incrementsCorrectlyAndSeparatesKeys() {
        InMemoryChatUsageCounterStore store = new InMemoryChatUsageCounterStore();

        assertEquals(1, store.incrementAndGet("u1", 10));
        assertEquals(2, store.incrementAndGet("u1", 10));
        assertEquals(1, store.incrementAndGet("u2", 10));
        assertEquals(1, store.incrementAndGet("u1", 11));
    }
}
