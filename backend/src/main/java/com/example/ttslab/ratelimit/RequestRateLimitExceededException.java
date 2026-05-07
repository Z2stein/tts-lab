package com.example.ttslab.ratelimit;

import com.example.ttslab.prompts.ModelType;

public class RequestRateLimitExceededException extends RuntimeException {
    private final ModelType modelType;
    private final long retryAfterSeconds;
    private final long limit;
    private final long remaining;
    private final RequestRateLimitUnit unit;

    public RequestRateLimitExceededException(RequestRateLimitResult result) {
        super("Request usage limit exceeded");
        this.modelType = result.modelType();
        this.retryAfterSeconds = result.retryAfterSeconds();
        this.limit = result.limit();
        this.remaining = result.remaining();
        this.unit = result.unit();
    }

    public ModelType modelType() {
        return modelType;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public long limit() {
        return limit;
    }

    public long remaining() {
        return remaining;
    }

    public RequestRateLimitUnit unit() {
        return unit;
    }
}

