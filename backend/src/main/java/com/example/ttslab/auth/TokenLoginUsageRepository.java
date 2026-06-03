package com.example.ttslab.auth;

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

/**
 * Persists one row per successful demo-token login into the dedicated
 * {@code token_login_usage} table. Backend-only: there is no controller or
 * API surface exposing this data.
 */
@Repository
public class TokenLoginUsageRepository {
    private final JdbcTemplate jdbcTemplate;

    public TokenLoginUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long recordLogin(String tokenJti, String tokenName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO token_login_usage (token_jti, token_name)
                VALUES (?, ?)
                """, new String[] {"id"});
            statement.setString(1, tokenJti);
            statement.setString(2, tokenName);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public List<TokenLoginUsage> findByJti(String tokenJti) {
        return jdbcTemplate.query("""
            SELECT id, token_jti, token_name, used_at
            FROM token_login_usage
            WHERE token_jti = ?
            ORDER BY used_at DESC, id DESC
            """, rowMapper(), tokenJti);
    }

    private RowMapper<TokenLoginUsage> rowMapper() {
        return (rs, rowNum) -> new TokenLoginUsage(
            rs.getLong("id"),
            rs.getString("token_jti"),
            rs.getString("token_name"),
            instant(rs, "used_at")
        );
    }

    private Instant instant(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
