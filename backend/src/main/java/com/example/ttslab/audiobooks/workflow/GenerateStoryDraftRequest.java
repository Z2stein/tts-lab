package com.example.ttslab.audiobooks.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerateStoryDraftRequest(
    @NotBlank @Size(max = 500) String idea,
    List<String> enhancements
) {}
