package com.example.ttslab.prompts;

import com.example.ttslab.auth.CurrentUser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PromptHistoryService {
    private static final int DEFAULT_LIMIT = 100;

    private final PromptHistoryRepository repository;

    public PromptHistoryService(PromptHistoryRepository repository) {
        this.repository = repository;
    }

    public void record(CurrentUser user, ModelType modelType, String providerModelName, String promptText, PromptRequestStatus status) {
        if (promptText == null || promptText.isBlank()) {
            return;
        }
        repository.save(user, modelType, blankToNull(providerModelName), promptText, status);
    }

    public List<PromptHistoryItem> findForUser(CurrentUser user, ModelType modelType) {
        return repository.findForUser(user.id(), modelType, DEFAULT_LIMIT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
