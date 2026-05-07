package com.example.ttslab.ratelimit;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.chat.ChatUsageWindowCalculator;
import com.example.ttslab.prompts.ModelType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestRateLimitService {
    private final RequestRateLimitProperties properties;
    private final RequestRateLimitRepository repository;
    private final ChatUsageWindowCalculator windowCalculator;
    private final Clock clock;

    public RequestRateLimitService(RequestRateLimitProperties properties, RequestRateLimitRepository repository,
                                   ChatUsageWindowCalculator windowCalculator, Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.windowCalculator = windowCalculator;
        this.clock = clock;
    }

    @Transactional
    public RequestRateLimitResult checkAndConsume(CurrentUser user, ModelType modelType, long amount) {
        long safeAmount = Math.max(0, amount);
        long bucket = windowBucket();
        long limit = effectiveLimit(user.id(), modelType);
        long used = repository.usedAmount(user.id(), modelType, bucket);

        if (!properties.enabled() || safeAmount == 0) {
            return result(modelType, true, used, limit, safeAmount, bucket);
        }

        if (used + safeAmount > limit) {
            return result(modelType, false, used, limit, safeAmount, bucket);
        }

        repository.consume(user.id(), modelType, bucket, properties.unit(), safeAmount);
        return result(modelType, true, used + safeAmount, limit, safeAmount, bucket);
    }

    public RequestRateLimitSummaryResponse summary(CurrentUser user) {
        long bucket = windowBucket();
        List<RequestRateLimitSummaryItem> limits = List.of(ModelType.SPEECH_MODEL, ModelType.TEXT_MODEL).stream()
            .map(modelType -> {
                long limit = effectiveLimit(user.id(), modelType);
                long used = repository.usedAmount(user.id(), modelType, bucket);
                return new RequestRateLimitSummaryItem(modelType, used, limit, Math.max(0, limit - used), properties.unit());
            })
            .toList();
        return new RequestRateLimitSummaryResponse(
            bucketResetAt(bucket),
            properties.window().toSeconds(),
            limits
        );
    }

    public RequestRateLimitUnit unit() {
        return properties.unit();
    }

    private RequestRateLimitResult result(ModelType modelType, boolean allowed, long used, long limit, long requested, long bucket) {
        return new RequestRateLimitResult(
            modelType,
            allowed,
            used,
            limit,
            Math.max(0, limit - used),
            requested,
            windowCalculator.retryAfterSeconds(clock.instant(), properties.window()),
            bucket,
            properties.unit()
        );
    }

    private long effectiveLimit(String userId, ModelType modelType) {
        return repository.findOverrideLimit(userId, modelType)
            .orElseGet(() -> defaultLimit(modelType));
    }

    private long defaultLimit(ModelType modelType) {
        if (modelType == ModelType.TEXT_MODEL) {
            return properties.speechModelLimit() * properties.textModelMultiplier();
        }
        return properties.speechModelLimit();
    }

    private long windowBucket() {
        return windowCalculator.windowBucket(clock.instant(), properties.window());
    }

    private Instant bucketResetAt(long bucket) {
        return Instant.ofEpochSecond((bucket + 1) * properties.window().toSeconds());
    }
}
