package com.example.ttslab.prompts;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ttslab.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@Import(PromptHistoryRepository.class)
@DisplayName("PromptHistoryRepository Extended")
class PromptHistoryRepositoryExtendedTest {

    @Autowired
    private PromptHistoryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final CurrentUser user1 = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");
    private final CurrentUser user2 = new CurrentUser("user-2", "user2@example.com", "User Two", List.of("USER"), "mock");

    @BeforeEach
    void setup() {
        // Clean up before each test
        jdbcTemplate.execute("DELETE FROM prompt_history");
        jdbcTemplate.execute("DELETE FROM prompt_usage");
    }

    // ============ SAVE TESTS ============

    @Test
    @DisplayName("save returns positive ID for successful insert")
    void saveSinglePrompt() {
        long id = repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Test prompt", PromptRequestStatus.SUCCESS);

        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("save persists prompt with all attributes")
    void savePersistsAllAttributes() {
        repository.save(user1, ModelType.TEXT_MODEL, "claude-3", "Complete test", PromptRequestStatus.FAILED);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items).hasSize(1);
        PromptHistoryItem item = items.getFirst();
        assertThat(item.userId()).isEqualTo("user-1");
        assertThat(item.userEmail()).isEqualTo("user1@example.com");
        assertThat(item.modelType()).isEqualTo(ModelType.TEXT_MODEL);
        assertThat(item.providerModelName()).isEqualTo("claude-3");
        assertThat(item.promptText()).isEqualTo("Complete test");
        assertThat(item.requestStatus()).isEqualTo(PromptRequestStatus.FAILED);
    }

    @Test
    @DisplayName("save with null provider model name persists null")
    void saveNullProviderModelName() {
        repository.save(user1, ModelType.SPEECH_MODEL, null, "Speak test", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().providerModelName()).isNull();
    }

    @Test
    @DisplayName("save increments prompt usage for new user")
    void saveIncrementsUsageNewUser() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "First prompt", PromptRequestStatus.SUCCESS);

        Long count = jdbcTemplate.queryForObject(
            "SELECT request_count FROM prompt_usage WHERE user_id = ? AND model_type = ?",
            Long.class,
            user1.id(),
            ModelType.TEXT_MODEL.name()
        );

        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("save increments prompt usage for existing user")
    void saveIncrementsUsageExistingUser() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "First", PromptRequestStatus.SUCCESS);
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Second", PromptRequestStatus.SUCCESS);

        Long count = jdbcTemplate.queryForObject(
            "SELECT request_count FROM prompt_usage WHERE user_id = ? AND model_type = ?",
            Long.class,
            user1.id(),
            ModelType.TEXT_MODEL.name()
        );

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("save tracks usage separately for different model types")
    void saveSeparateUsagePerModelType() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Text", PromptRequestStatus.SUCCESS);
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Text 2", PromptRequestStatus.SUCCESS);
        repository.save(user1, ModelType.SPEECH_MODEL, "tts", "Speech", PromptRequestStatus.SUCCESS);

        Long textCount = jdbcTemplate.queryForObject(
            "SELECT request_count FROM prompt_usage WHERE user_id = ? AND model_type = ?",
            Long.class,
            user1.id(),
            ModelType.TEXT_MODEL.name()
        );
        Long speechCount = jdbcTemplate.queryForObject(
            "SELECT request_count FROM prompt_usage WHERE user_id = ? AND model_type = ?",
            Long.class,
            user1.id(),
            ModelType.SPEECH_MODEL.name()
        );

        assertThat(textCount).isEqualTo(2L);
        assertThat(speechCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("save tracks usage separately for different users")
    void saveSeparateUsagePerUser() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "User 1 prompt", PromptRequestStatus.SUCCESS);
        repository.save(user2, ModelType.TEXT_MODEL, "gpt-4", "User 2 prompt", PromptRequestStatus.SUCCESS);
        repository.save(user2, ModelType.TEXT_MODEL, "gpt-4", "User 2 second", PromptRequestStatus.SUCCESS);

        Long user1Count = jdbcTemplate.queryForObject(
            "SELECT request_count FROM prompt_usage WHERE user_id = ? AND model_type = ?",
            Long.class,
            user1.id(),
            ModelType.TEXT_MODEL.name()
        );
        Long user2Count = jdbcTemplate.queryForObject(
            "SELECT request_count FROM prompt_usage WHERE user_id = ? AND model_type = ?",
            Long.class,
            user2.id(),
            ModelType.TEXT_MODEL.name()
        );

        assertThat(user1Count).isEqualTo(1L);
        assertThat(user2Count).isEqualTo(2L);
    }

    @Test
    @DisplayName("save persists success status")
    void saveSaveSuccessStatus() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Test", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().requestStatus()).isEqualTo(PromptRequestStatus.SUCCESS);
    }

    @Test
    @DisplayName("save persists failed status")
    void saveSaveFailedStatus() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Test", PromptRequestStatus.FAILED);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items.getFirst().requestStatus()).isEqualTo(PromptRequestStatus.FAILED);
    }

    @Test
    @DisplayName("save persists rate-limited status")
    void saveSaveRateLimitedStatus() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Test", PromptRequestStatus.RATE_LIMITED);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items.getFirst().requestStatus()).isEqualTo(PromptRequestStatus.RATE_LIMITED);
    }

    // ============ FIND FOR USER TESTS ============

    @Test
    @DisplayName("findForUser with null modelType returns all items for user")
    void findForUserAllModelTypes() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Text", PromptRequestStatus.SUCCESS);
        repository.save(user1, ModelType.SPEECH_MODEL, "tts", "Speech", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items).hasSize(2);
        assertThat(items).extracting(PromptHistoryItem::modelType)
            .containsExactlyInAnyOrder(ModelType.TEXT_MODEL, ModelType.SPEECH_MODEL);
    }

    @Test
    @DisplayName("findForUser with specific modelType filters correctly")
    void findForUserFilterByModelType() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Text", PromptRequestStatus.SUCCESS);
        repository.save(user1, ModelType.SPEECH_MODEL, "tts", "Speech", PromptRequestStatus.SUCCESS);
        repository.save(user1, ModelType.TEXT_MODEL, "claude", "Text 2", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> textItems = repository.findForUser(user1.id(), ModelType.TEXT_MODEL, 10);

        assertThat(textItems).hasSize(2);
        assertThat(textItems).allMatch(item -> item.modelType() == ModelType.TEXT_MODEL);
    }

    @Test
    @DisplayName("findForUser filters by user ID")
    void findForUserFilterByUserId() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "User 1", PromptRequestStatus.SUCCESS);
        repository.save(user2, ModelType.TEXT_MODEL, "gpt-4", "User 2", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> user1Items = repository.findForUser(user1.id(), null, 10);

        assertThat(user1Items).hasSize(1);
        assertThat(user1Items.getFirst().userId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("findForUser respects limit parameter")
    void findForUserRespectLimit() {
        for (int i = 0; i < 5; i++) {
            repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Prompt " + i, PromptRequestStatus.SUCCESS);
        }

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 3);

        assertThat(items).hasSize(3);
    }

    @Test
    @DisplayName("findForUser returns items in reverse chronological order")
    void findForUserReturnsDescending() throws InterruptedException {
        // Save in specific order
        long id1 = repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "First", PromptRequestStatus.SUCCESS);
        Thread.sleep(10); // Small delay to ensure different timestamps
        long id2 = repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Second", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        // Most recent first
        assertThat(items).hasSize(2);
        assertThat(items.get(0).id()).isEqualTo(id2);
        assertThat(items.get(1).id()).isEqualTo(id1);
    }

    @Test
    @DisplayName("findForUser returns items ordered by created_at descending")
    void findForUserReturnsItemsInDescending() throws InterruptedException {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "First", PromptRequestStatus.SUCCESS);
        Thread.sleep(10);
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Second", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).promptText()).isEqualTo("Second");
        assertThat(items.get(1).promptText()).isEqualTo("First");
    }

    @Test
    @DisplayName("findForUser returns empty list for user with no history")
    void findForUserEmptyHistory() {
        List<PromptHistoryItem> items = repository.findForUser("nonexistent-user", null, 10);

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("findForUser with modelType filter returns empty when no matches")
    void findForUserEmptyWithModelTypeFilter() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Text", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), ModelType.SPEECH_MODEL, 10);

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("findForUser returns items with complete data")
    void findForUserCompleteData() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Complete data test", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items).hasSize(1);
        PromptHistoryItem item = items.getFirst();
        assertThat(item.id()).isPositive();
        assertThat(item.userId()).isEqualTo("user-1");
        assertThat(item.userEmail()).isEqualTo("user1@example.com");
        assertThat(item.modelType()).isEqualTo(ModelType.TEXT_MODEL);
        assertThat(item.providerModelName()).isEqualTo("gpt-4");
        assertThat(item.promptText()).isEqualTo("Complete data test");
        assertThat(item.requestStatus()).isEqualTo(PromptRequestStatus.SUCCESS);
        assertThat(item.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("findForUser retrieves timestamp")
    void findForUserIncludesTimestamp() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Test", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items.getFirst().createdAt()).isNotNull();
        // Timestamp should be recent (within last minute)
        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minusSeconds(60);
        assertThat(items.getFirst().createdAt()).isAfter(oneMinuteAgo).isBefore(now.plusSeconds(1));
    }

    // ============ LARGE DATA TESTS ============

    @Test
    @DisplayName("findForUser works with large limit")
    void findForUserLargeLimit() {
        for (int i = 0; i < 10; i++) {
            repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Prompt " + i, PromptRequestStatus.SUCCESS);
        }

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 1000);

        assertThat(items).hasSize(10);
    }

    @Test
    @DisplayName("findForUser with limit=0 returns empty")
    void findForUserZeroLimit() {
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", "Prompt", PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 0);

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("save handles long prompt text")
    void saveLongPromptText() {
        String longPrompt = "A".repeat(1000);
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", longPrompt, PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items.getFirst().promptText()).isEqualTo(longPrompt);
    }

    @Test
    @DisplayName("save handles special characters in prompt text")
    void saveSpecialCharactersInPrompt() {
        String specialPrompt = "Test with special chars: \"'\\n\\t<>&";
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", specialPrompt, PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items.getFirst().promptText()).isEqualTo(specialPrompt);
    }

    @Test
    @DisplayName("save handles unicode characters in prompt")
    void saveUnicodeInPrompt() {
        String unicodePrompt = "Unicode test: 你好世界 🚀 Привет";
        repository.save(user1, ModelType.TEXT_MODEL, "gpt-4", unicodePrompt, PromptRequestStatus.SUCCESS);

        List<PromptHistoryItem> items = repository.findForUser(user1.id(), null, 10);

        assertThat(items.getFirst().promptText()).isEqualTo(unicodePrompt);
    }
}
