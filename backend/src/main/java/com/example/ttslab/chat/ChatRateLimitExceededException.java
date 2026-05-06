package com.example.ttslab.chat;

public class ChatRateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;
    private final String window;
    private final int maxRequests;

    public ChatRateLimitExceededException(long retryAfterSeconds, String window, int maxRequests) {
        super("Chat usage limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
        this.window = window;
        this.maxRequests = maxRequests;
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }
    public String window() { return window; }
    public int maxRequests() { return maxRequests; }
}
