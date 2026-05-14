package com.example.ttslab.prompts;

import com.example.ttslab.auth.CurrentUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PromptHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    public PromptHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long save(CurrentUser user, ModelType modelType, String providerModelName, String promptText, PromptRequestStatus status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO prompt_history (user_id, user_email, model_type, provider_model_name, prompt_text, request_status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, new String[] {"id"});
            statement.setString(1, user.id());
            statement.setString(2, user.email());
            statement.setString(3, modelType.name());
            statement.setString(4, providerModelName);
            statement.setString(5, promptText);
            statement.setString(6, status.name());
            return statement;
        }, keyHolder);

        incrementUsage(user.id(), modelType);
        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public List<PromptHistoryItem> findForUser(String userId, ModelType modelType, int limit) {
        if (modelType == null) {
            return jdbcTemplate.query("""
                SELECT id, user_id, user_email, model_type, provider_model_name, prompt_text, request_status, created_at
                FROM prompt_history
                WHERE user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """, rowMapper(), userId, limit);
        }

        return jdbcTemplate.query("""
            SELECT id, user_id, user_email, model_type, provider_model_name, prompt_text, request_status, created_at
            FROM prompt_history
            WHERE user_id = ? AND model_type = ?
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """, rowMapper(), userId, modelType.name(), limit);
    }

    private void incrementUsage(String userId, ModelType modelType) {
        int updated = jdbcTemplate.update("""
            UPDATE prompt_usage
            SET request_count = request_count + 1, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND model_type = ?
            """, userId, modelType.name());

        if (updated == 0) {
            jdbcTemplate.update("""
                INSERT INTO prompt_usage (user_id, model_type, request_count, updated_at)
                VALUES (?, ?, 1, CURRENT_TIMESTAMP)
                """, userId, modelType.name());
        }
    }

    private RowMapper<PromptHistoryItem> rowMapper() {
        return (rs, rowNum) -> new PromptHistoryItem(
            rs.getLong("id"),
            rs.getString("user_id"),
            rs.getString("user_email"),
            ModelType.valueOf(rs.getString("model_type")),
            rs.getString("provider_model_name"),
            rs.getString("prompt_text"),
            PromptRequestStatus.valueOf(rs.getString("request_status")),
            instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
