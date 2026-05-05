package com.example.ttslab.chat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryChatUsageCounterStore implements ChatUsageCounterStore {
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long incrementAndGet(String identifier, long windowBucket) {
        String key = identifier + ":" + windowBucket;
        return counters.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }
}
