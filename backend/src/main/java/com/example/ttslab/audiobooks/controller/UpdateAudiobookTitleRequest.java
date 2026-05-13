package com.example.ttslab.audiobooks.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAudiobookTitleRequest(
    @NotBlank
    @Size(max = 255)
    String title
) {
}
