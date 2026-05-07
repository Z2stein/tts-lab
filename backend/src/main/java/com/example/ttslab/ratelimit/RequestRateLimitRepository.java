package com.example.ttslab.ratelimit;

import com.example.ttslab.prompts.ModelType;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RequestRateLimitRepository {
    private final JdbcTemplate jdbcTemplate;

    public RequestRateLimitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findOverrideLimit(String userId, ModelType modelType) {
        return jdbcTemplate.query("""
            SELECT limit_amount
            FROM request_rate_limit_overrides
            WHERE user_id = ? AND model_type = ?
            """, rs -> rs.next() ? Optional.of(rs.getLong("limit_amount")) : Optional.empty(), userId, modelType.name());
    }

    public long usedAmount(String userId, ModelType modelType, long windowBucket) {
        return jdbcTemplate.query("""
            SELECT used_amount
            FROM request_usage_windows
            WHERE user_id = ? AND model_type = ? AND window_bucket = ?
            """, rs -> rs.next() ? rs.getLong("used_amount") : 0L, userId, modelType.name(), windowBucket);
    }

    public void consume(String userId, ModelType modelType, long windowBucket, RequestRateLimitUnit unit, long amount) {
        ensureUsageRow(userId, modelType, windowBucket, unit);
        jdbcTemplate.update("""
            UPDATE request_usage_windows
            SET used_amount = used_amount + ?, unit = ?, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND model_type = ? AND window_bucket = ?
            """, amount, unit.name(), userId, modelType.name(), windowBucket);
    }

    private void ensureUsageRow(String userId, ModelType modelType, long windowBucket, RequestRateLimitUnit unit) {
        try {
            jdbcTemplate.update("""
                INSERT INTO request_usage_windows (user_id, model_type, window_bucket, unit, used_amount, updated_at)
                VALUES (?, ?, ?, ?, 0, CURRENT_TIMESTAMP)
                """, userId, modelType.name(), windowBucket, unit.name());
        } catch (DuplicateKeyException ignored) {
            // Existing rows are the normal path once a user has made a request in the window.
        }
    }
}

