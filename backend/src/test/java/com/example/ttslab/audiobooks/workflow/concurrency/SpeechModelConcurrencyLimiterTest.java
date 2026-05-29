package com.example.ttslab.audiobooks.workflow.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ttslab.error.ApiException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SpeechModelConcurrencyLimiterTest {

    private static SpeechModelConcurrencyLimiter limiter(boolean enabled, int maxConcurrent, Duration acquireTimeout) {
        return new SpeechModelConcurrencyLimiter(
            new SpeechModelConcurrencyProperties(enabled, maxConcurrent, acquireTimeout)
        );
    }

    @Test
    void secondCallBlocksUntilFirstReleasesPermit() throws InterruptedException {
        SpeechModelConcurrencyLimiter limiter = limiter(true, 1, Duration.ofSeconds(5));
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean secondStarted = new AtomicBoolean(false);

        Thread first = new Thread(() -> limiter.callWithPermit(() -> {
            firstInside.countDown();
            try {
                releaseFirst.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "first";
        }));
        first.start();

        assertThat(firstInside.await(2, TimeUnit.SECONDS)).isTrue();

        Thread second = new Thread(() -> limiter.callWithPermit(() -> {
            secondStarted.set(true);
            return "second";
        }));
        second.start();

        // While the first holds the only permit, the second must not enter.
        Thread.sleep(200);
        assertThat(secondStarted.get()).isFalse();

        releaseFirst.countDown();
        second.join(2000);
        first.join(2000);
        assertThat(secondStarted.get()).isTrue();
    }

    @Test
    void throwsBusyApiExceptionWhenAcquireTimesOut() throws InterruptedException {
        SpeechModelConcurrencyLimiter limiter = limiter(true, 1, Duration.ofMillis(50));
        CountDownLatch holderInside = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);

        Thread holder = new Thread(() -> limiter.callWithPermit(() -> {
            holderInside.countDown();
            try {
                releaseHolder.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "holder";
        }));
        holder.start();
        assertThat(holderInside.await(2, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> limiter.callWithPermit(() -> "blocked"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException apiException = (ApiException) ex;
                assertThat(apiException.code()).isEqualTo("TTS_AUDIO_PROVIDER_BUSY");
                assertThat(apiException.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            });

        releaseHolder.countDown();
        holder.join(2000);
    }

    @Test
    void releasesPermitWhenActionThrows() {
        SpeechModelConcurrencyLimiter limiter = limiter(true, 1, Duration.ofSeconds(1));

        assertThatThrownBy(() -> limiter.callWithPermit(() -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        // Permit must be free again: a subsequent call succeeds without timing out.
        String result = limiter.callWithPermit(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void disabledLimiterPassesThroughWithoutGating() {
        SpeechModelConcurrencyLimiter limiter = limiter(false, 1, Duration.ofMillis(1));

        // Even with maxConcurrent=1 and a tiny timeout, nested calls work because gating is off.
        String result = limiter.callWithPermit(() -> limiter.callWithPermit(() -> "nested"));
        assertThat(result).isEqualTo("nested");
    }
}
