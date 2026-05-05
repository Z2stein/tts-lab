package com.example.ttslab.chat;

public interface ChatUsageCounterStore {
    long incrementAndGet(String identifier, long windowBucket);
}
