package com.example.ttslab.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@Import(TokenLoginUsageRepository.class)
class TokenLoginUsageRepositoryTest {
    @Autowired
    private TokenLoginUsageRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTableAndRecordLoginPersistsRow() {
        long id = repository.recordLogin("jti-1", "Demo Visitor");

        assertThat(id).isPositive();

        Long rowCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM token_login_usage WHERE token_jti = ?",
            Long.class,
            "jti-1"
        );
        assertThat(rowCount).isEqualTo(1L);
    }

    @Test
    void recordLoginLogsOneRowPerUsage() {
        repository.recordLogin("jti-2", "Repeat Visitor");
        repository.recordLogin("jti-2", "Repeat Visitor");

        List<TokenLoginUsage> usages = repository.findByJti("jti-2");

        assertThat(usages).hasSize(2);
        assertThat(usages).allSatisfy(usage -> {
            assertThat(usage.tokenName()).isEqualTo("Repeat Visitor");
            assertThat(usage.usedAt()).isNotNull();
        });
    }

    @Test
    void findByJtiOnlyReturnsMatchingToken() {
        repository.recordLogin("jti-a", "User A");
        repository.recordLogin("jti-b", "User B");

        List<TokenLoginUsage> usages = repository.findByJti("jti-a");

        assertThat(usages).extracting(TokenLoginUsage::tokenName).containsExactly("User A");
    }
}
