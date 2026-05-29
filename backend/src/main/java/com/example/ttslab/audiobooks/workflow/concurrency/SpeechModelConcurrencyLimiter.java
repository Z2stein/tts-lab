package com.example.ttslab.audiobooks.workflow.concurrency;

import com.example.ttslab.error.ApiException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Caps the number of speech-model requests running concurrently across the whole backend.
 * Callers block until a permit is free; if none becomes available within the configured
 * acquire timeout, the request fails with a structured 503 so the caller can retry.
 */
@Component
public class SpeechModelConcurrencyLimiter {

    private final boolean enabled;
    private final long acquireTimeoutMillis;
    private final Semaphore semaphore;

    public SpeechModelConcurrencyLimiter(SpeechModelConcurrencyProperties properties) {
        this.enabled = properties.enabled();
        this.acquireTimeoutMillis = properties.acquireTimeout().toMillis();
        // Fair (FIFO) ordering keeps queued requests from starving under sustained load.
        this.semaphore = new Semaphore(properties.maxConcurrent(), true);
    }

    public <T> T callWithPermit(Supplier<T> action) {
        if (!enabled) {
            return action.get();
        }

        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw busy(ex);
        }

        if (!acquired) {
            throw busy(null);
        }

        try {
            return action.get();
        } finally {
            semaphore.release();
        }
    }

    private ApiException busy(Throwable cause) {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "TTS_AUDIO_PROVIDER_BUSY",
            "The speech service is busy. Please try again shortly.",
            null,
            cause
        );
    }
}
