package com.example.ttslab.audiobooks;

/**
 * Calculates audiobook metadata on-demand from entities.
 *
 * This service implements the single source of truth principle:
 * segments and characters are the source of truth for counts.
 */
public class AudiobookMetadataCalculator {
    private final AudiobookRepository repository;

    public AudiobookMetadataCalculator(AudiobookRepository repository) {
        this.repository = repository;
    }

    /**
     * Calculate the number of speech segments for an audiobook.
     *
     * @param projectId the audiobook project ID
     * @return the number of speech segments
     */
    public int calculateSceneCount(String projectId) {
        return repository.findSegmentsForProject(projectId).size();
    }

    /**
     * Calculate the number of unique characters in the audiobook.
     *
     * @param projectId the audiobook project ID
     * @return the count of unique characters
     */
    public int calculateSpeakerCount(String projectId) {
        return repository.findCharactersForProject(projectId).size();
    }

    /**
     * Calculate total duration by summing all audio asset durations.
     *
     * @param projectId the audiobook project ID
     * @return total duration in seconds
     */
    public int calculateTotalDurationSeconds(String projectId) {
        return (int) (repository.findAssetsForProject(projectId).stream()
            .mapToLong(asset -> asset.durationMs() != null ? asset.durationMs() : 0)
            .sum() / 1000);
    }
}
