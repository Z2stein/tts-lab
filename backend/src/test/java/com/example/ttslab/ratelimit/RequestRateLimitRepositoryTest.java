package com.example.ttslab.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ttslab.prompts.ModelType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@Import(RequestRateLimitRepository.class)
class RequestRateLimitRepositoryTest {
    @Autowired
    private RequestRateLimitRepository repository;

    @Test
    void consumeCanUpdateExistingWindowWithoutAbortingTransaction() {
        repository.consume("user-1", ModelType.TEXT_MODEL, 41161L, RequestRateLimitUnit.WORDS, 3L);
        repository.consume("user-1", ModelType.TEXT_MODEL, 41161L, RequestRateLimitUnit.WORDS, 4L);

        assertThat(repository.usedAmount("user-1", ModelType.TEXT_MODEL, 41161L)).isEqualTo(7L);
    }
}
