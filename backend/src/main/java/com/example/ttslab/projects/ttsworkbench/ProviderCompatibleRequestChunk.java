package com.example.ttslab.projects.ttsworkbench;

import java.util.List;

public record ProviderCompatibleRequestChunk(
    int chunkNumber,
    List<String> speakers,
    FinalTtsRequestPreviewResponse request
) {
}
