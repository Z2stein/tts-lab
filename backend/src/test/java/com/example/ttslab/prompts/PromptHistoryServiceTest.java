package com.example.ttslab.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ttslab.auth.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PromptHistoryService")
class PromptHistoryServiceTest {

    private final PromptHistoryRepository mockRepository = mock(PromptHistoryRepository.class);
    private final PromptHistoryService service = new PromptHistoryService(mockRepository);
    private final CurrentUser testUser = new CurrentUser("user-1", "user@example.com", "Test User", List.of(), "mock");

    // ============ record() Tests ============

    @Test
    @DisplayName("record saves valid prompt to repository")
    void recordSavesValidPrompt() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.TEXT_MODEL, "gpt-4", "Hello", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            eq(testUser),
            eq(ModelType.TEXT_MODEL),
            eq("gpt-4"),
            eq("Hello"),
            eq(PromptRequestStatus.SUCCESS)
        );
    }

    @Test
    @DisplayName("record does not save null prompt text")
    void recordIgnoresNullPrompt() {
        service.record(testUser, ModelType.TEXT_MODEL, "gpt-4", null, PromptRequestStatus.SUCCESS);

        verify(mockRepository, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("record does not save blank prompt text")
    void recordIgnoresBlankPrompt() {
        service.record(testUser, ModelType.TEXT_MODEL, "gpt-4", "   ", PromptRequestStatus.SUCCESS);

        verify(mockRepository, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("record does not save empty prompt text")
    void recordIgnoresEmptyPrompt() {
        service.record(testUser, ModelType.TEXT_MODEL, "gpt-4", "", PromptRequestStatus.SUCCESS);

        verify(mockRepository, never()).save(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("record converts blank provider model name to null")
    void recordConvertsBlankProviderNameToNull() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.TEXT_MODEL, "   ", "Valid prompt", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            eq(testUser),
            eq(ModelType.TEXT_MODEL),
            eq(null),
            eq("Valid prompt"),
            eq(PromptRequestStatus.SUCCESS)
        );
    }

    @Test
    @DisplayName("record converts null provider model name to null")
    void recordConvertsNullProviderNameToNull() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.TEXT_MODEL, null, "Valid prompt", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            eq(testUser),
            eq(ModelType.TEXT_MODEL),
            eq(null),
            eq("Valid prompt"),
            eq(PromptRequestStatus.SUCCESS)
        );
    }

    @Test
    @DisplayName("record preserves non-blank provider model name")
    void recordPreservesProviderName() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.TEXT_MODEL, "claude-3", "Valid prompt", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            eq(testUser),
            eq(ModelType.TEXT_MODEL),
            eq("claude-3"),
            eq("Valid prompt"),
            eq(PromptRequestStatus.SUCCESS)
        );
    }

    @Test
    @DisplayName("record saves status from any PromptRequestStatus value")
    void recordSavesStatusSuccess() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.SPEECH_MODEL, "tts-service", "Speak", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            any(), any(), any(), any(), eq(PromptRequestStatus.SUCCESS)
        );
    }

    @Test
    @DisplayName("record saves failed status")
    void recordSavesStatusFailed() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.SPEECH_MODEL, "tts-service", "Speak", PromptRequestStatus.FAILED);

        verify(mockRepository).save(
            any(), any(), any(), any(), eq(PromptRequestStatus.FAILED)
        );
    }

    @Test
    @DisplayName("record saves rate-limited status")
    void recordSavesStatusRateLimited() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.SPEECH_MODEL, "tts-service", "Speak", PromptRequestStatus.RATE_LIMITED);

        verify(mockRepository).save(
            any(), any(), any(), any(), eq(PromptRequestStatus.RATE_LIMITED)
        );
    }

    @Test
    @DisplayName("record saves TEXT_MODEL")
    void recordSavesTextModel() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.TEXT_MODEL, "gpt-4", "Text prompt", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            any(), eq(ModelType.TEXT_MODEL), any(), any(), any()
        );
    }

    @Test
    @DisplayName("record saves SPEECH_MODEL")
    void recordSavesSpeechModel() {
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(testUser, ModelType.SPEECH_MODEL, "tts-service", "Speech prompt", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            any(), eq(ModelType.SPEECH_MODEL), any(), any(), any()
        );
    }

    @Test
    @DisplayName("record saves with different user IDs")
    void recordSavesDifferentUsers() {
        CurrentUser anotherUser = new CurrentUser("user-2", "user2@example.com", "Another User", List.of(), "mock");
        when(mockRepository.save(any(), any(), any(), any(), any())).thenReturn(1L);

        service.record(anotherUser, ModelType.TEXT_MODEL, "gpt-4", "Prompt", PromptRequestStatus.SUCCESS);

        verify(mockRepository).save(
            eq(anotherUser), any(), any(), any(), any()
        );
    }

    // ============ findForUser() Tests ============

    @Test
    @DisplayName("findForUser returns items from repository without model type filter")
    void findForUserWithoutModelType() {
        PromptHistoryItem item = new PromptHistoryItem(
            1L, "user-1", "user@example.com", ModelType.TEXT_MODEL, "gpt-4", "Hello", PromptRequestStatus.SUCCESS, Instant.now()
        );
        when(mockRepository.findForUser("user-1", null, 100)).thenReturn(List.of(item));

        List<PromptHistoryItem> result = service.findForUser(testUser, null);

        assertThat(result).hasSize(1).contains(item);
    }

    @Test
    @DisplayName("findForUser returns items filtered by model type")
    void findForUserWithModelType() {
        PromptHistoryItem item = new PromptHistoryItem(
            2L, "user-1", "user@example.com", ModelType.SPEECH_MODEL, "tts-service", "Speak", PromptRequestStatus.SUCCESS, Instant.now()
        );
        when(mockRepository.findForUser("user-1", ModelType.SPEECH_MODEL, 100)).thenReturn(List.of(item));

        List<PromptHistoryItem> result = service.findForUser(testUser, ModelType.SPEECH_MODEL);

        assertThat(result).hasSize(1).contains(item);
    }

    @Test
    @DisplayName("findForUser uses default limit of 100")
    void findForUserDefaultLimit() {
        when(mockRepository.findForUser("user-1", null, 100)).thenReturn(List.of());

        service.findForUser(testUser, null);

        verify(mockRepository).findForUser("user-1", null, 100);
    }

    @Test
    @DisplayName("findForUser returns empty list when no items found")
    void findForUserEmptyResult() {
        when(mockRepository.findForUser("user-1", ModelType.TEXT_MODEL, 100)).thenReturn(List.of());

        List<PromptHistoryItem> result = service.findForUser(testUser, ModelType.TEXT_MODEL);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findForUser returns multiple items ordered from repository")
    void findForUserMultipleItems() {
        PromptHistoryItem item1 = new PromptHistoryItem(
            1L, "user-1", "user@example.com", ModelType.TEXT_MODEL, "gpt-4", "First", PromptRequestStatus.SUCCESS, Instant.parse("2026-05-10T12:00:00Z")
        );
        PromptHistoryItem item2 = new PromptHistoryItem(
            2L, "user-1", "user@example.com", ModelType.TEXT_MODEL, "claude", "Second", PromptRequestStatus.SUCCESS, Instant.parse("2026-05-10T13:00:00Z")
        );
        when(mockRepository.findForUser("user-1", null, 100)).thenReturn(List.of(item1, item2));

        List<PromptHistoryItem> result = service.findForUser(testUser, null);

        assertThat(result).hasSize(2).containsExactly(item1, item2);
    }

    @Test
    @DisplayName("findForUser passes user ID correctly")
    void findForUserPassesCorrectUserId() {
        CurrentUser specificUser = new CurrentUser("specific-user", "specific@example.com", "Specific", List.of(), "mock");
        when(mockRepository.findForUser("specific-user", null, 100)).thenReturn(List.of());

        service.findForUser(specificUser, null);

        verify(mockRepository).findForUser("specific-user", null, 100);
    }

    @Test
    @DisplayName("findForUser filters by TEXT_MODEL only")
    void findForUserTextModelFilter() {
        when(mockRepository.findForUser("user-1", ModelType.TEXT_MODEL, 100)).thenReturn(List.of());

        service.findForUser(testUser, ModelType.TEXT_MODEL);

        verify(mockRepository).findForUser("user-1", ModelType.TEXT_MODEL, 100);
    }

    @Test
    @DisplayName("findForUser filters by SPEECH_MODEL only")
    void findForUserSpeechModelFilter() {
        when(mockRepository.findForUser("user-1", ModelType.SPEECH_MODEL, 100)).thenReturn(List.of());

        service.findForUser(testUser, ModelType.SPEECH_MODEL);

        verify(mockRepository).findForUser("user-1", ModelType.SPEECH_MODEL, 100);
    }
}
