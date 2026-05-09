package com.example.ttslab.audiobooks;

import java.util.HashSet;
import java.util.Set;

/**
 * Calculates audiobook metadata on-demand from existing audio assets.
 *
 * This service implements the single source of truth principle: audio assets
 * are the source of truth, and all metadata is computed fresh from them.
 * This prevents stale metadata issues that occur when metadata is persisted
 * in the audiobook_project table.
 */
public class AudiobookMetadataCalculator {
    private final AudiobookRepository repository;

    public AudiobookMetadataCalculator(AudiobookRepository repository) {
        this.repository = repository;
    }

    /**
     * Calculate the number of speech segments (audio assets) for an audiobook.
     * Source of truth: count of READY audio assets.
     *
     * @param projectId the audiobook project ID
     * @return the number of ready audio assets
     */
    public int calculateSceneCount(String projectId) {
        return (int) repository.findAssets(projectId).stream()
            .filter(asset -> asset.status() == AudioAssetStatus.READY)
            .count();
    }

    /**
     * Calculate the number of unique speakers by extracting speaker names from assets.
     * Handles deduplication: same speaker appearing in multiple assets counts as one.
     * Falls back to asset filenames if speaker metadata is not available.
     *
     * @param projectId the audiobook project ID
     * @return the count of unique speakers
     */
    public int calculateSpeakerCount(String projectId) {
        Set<String> uniqueSpeakers = new HashSet<>();
        repository.findAssets(projectId).stream()
            .filter(asset -> asset.status() == AudioAssetStatus.READY)
            .forEach(asset -> {
                // Extract speaker from filename pattern or metadata
                String speaker = extractSpeakerFromAsset(asset);
                if (speaker != null && !speaker.isBlank()) {
                    uniqueSpeakers.add(speaker);
                }
            });
        return uniqueSpeakers.size();
    }

    /**
     * Calculate total duration by summing all READY audio asset durations.
     * Source of truth: sum of durationSeconds from all ready assets.
     *
     * @param projectId the audiobook project ID
     * @return total duration in seconds
     */
    public int calculateTotalDurationSeconds(String projectId) {
        return (int) repository.findAssets(projectId).stream()
            .filter(asset -> asset.status() == AudioAssetStatus.READY)
            .mapToInt(asset -> asset.durationSeconds() != null ? asset.durationSeconds() : 0)
            .sum();
    }

    /**
     * Extract speaker name from asset filename if following pattern: "segment-N-speakername.mp3"
     *
     * @param asset the audio asset
     * @return the extracted speaker name, or null if not found
     */
    private String extractSpeakerFromAsset(AudioAsset asset) {
        String filename = asset.filename();
        if (filename != null && filename.contains("-")) {
            String[] parts = filename.split("-");
            if (parts.length >= 2) {
                // Return the last meaningful part before extension
                String speakerPart = parts[parts.length - 1]
                    .replace(".mp3", "")
                    .replace(".mp4", "")
                    .replace(".wav", "");
                return speakerPart.isBlank() ? null : speakerPart;
            }
        }
        return null;
    }
}
