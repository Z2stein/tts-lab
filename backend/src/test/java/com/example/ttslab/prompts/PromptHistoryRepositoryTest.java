package com.example.ttslab.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ttslab.auth.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@Import(PromptHistoryRepository.class)
class PromptHistoryRepositoryTest {
    @Autowired
    private PromptHistoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesPromptTablesAndRepositoryPersistsPrompts() {
        CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");

        long id = repository.save(user, ModelType.TEXT_MODEL, "mock", "Hello text model", PromptRequestStatus.SUCCESS);

        assertThat(id).isPositive();
        List<PromptHistoryItem> items = repository.findForUser("user-1", null, 10);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().promptText()).isEqualTo("Hello text model");
        assertThat(items.getFirst().modelType()).isEqualTo(ModelType.TEXT_MODEL);

        Long requestCount = jdbcTemplate.queryForObject(
            "SELECT request_count FROM prompt_usage WHERE user_id = ? AND model_type = ?",
            Long.class,
            "user-1",
            ModelType.TEXT_MODEL.name()
        );
        assertThat(requestCount).isEqualTo(1L);
    }

    @Test
    void findForUserFiltersByUserAndModelType() {
        CurrentUser firstUser = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");
        CurrentUser secondUser = new CurrentUser("user-2", "user2@example.com", "User Two", List.of("USER"), "mock");

        repository.save(firstUser, ModelType.TEXT_MODEL, "mock", "Text prompt", PromptRequestStatus.SUCCESS);
        repository.save(firstUser, ModelType.SPEECH_MODEL, "google-tts", "Speech prompt", PromptRequestStatus.SUCCESS);
        repository.save(secondUser, ModelType.TEXT_MODEL, "mock", "Other user prompt", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> firstUserTextItems = repository.findForUser("user-1", ModelType.TEXT_MODEL, 10);

        assertThat(firstUserTextItems).extracting(PromptHistoryItem::promptText).containsExactly("Text prompt");
    }
}
