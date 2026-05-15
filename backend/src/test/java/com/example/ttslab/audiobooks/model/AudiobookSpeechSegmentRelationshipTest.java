package com.example.ttslab.audiobooks.model;

import com.example.ttslab.audiobooks.workflow.SpeakerVoice;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudiobookSpeechSegmentRelationshipTest {
    @Test
    void characterRelationshipIsLazyLoaded() {
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        SpeakerCharacter character = new SpeakerCharacter(
            "character-1",
            "project-1",
            0,
            "Alice",
            "Lead",
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Hello",
            null,
            character
        );

        assertThat(segment.getCharacter()).isEqualTo(character);
    }

    @Test
    void getCharacterIdReturnsIdFromRelatedCharacterEntity() {
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        SpeakerCharacter character = new SpeakerCharacter(
            "character-1",
            "project-1",
            0,
            "Alice",
            "Lead",
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Hello",
            null,
            character
        );

        assertThat(segment.getCharacterId()).isEqualTo("character-1");
    }

    @Test
    void segmentFetchesSpeakerNameFromCharacter() {
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        SpeakerCharacter character = new SpeakerCharacter(
            "character-1",
            "project-1",
            0,
            "Alice",
            "Lead",
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Hello",
            null,
            character
        );

        assertThat(segment.getCharacter().getSpeakerName()).isEqualTo("Alice");
    }

    @Test
    void setCharacterUpdatesRelationship() {
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        SpeakerCharacter character = new SpeakerCharacter(
            "character-1",
            "project-1",
            0,
            "Alice",
            "Lead",
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        SpeakerCharacter initialCharacter = new SpeakerCharacter(
            "character-0",
            "project-1",
            0,
            "InitialSpeaker",
            null,
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Hello",
            null,
            initialCharacter
        );

        segment.setCharacter(character);

        assertThat(segment.getCharacter()).isEqualTo(character);
        assertThat(segment.getCharacterId()).isEqualTo("character-1");
    }

    @Test
    void characterCanBeReassignedAfterInitialAssignment() {
        AudiobookProject project = new AudiobookProject(
            "project-1",
            "user-1",
            "Project",
            AudiobookProjectStatus.NEEDS_REVIEW,
            "AUDIOBOOK_WORKFLOW",
            0,
            null,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z")
        );
        SpeakerCharacter character1 = new SpeakerCharacter(
            "character-1",
            "project-1",
            0,
            "Alice",
            "Lead",
            SpeakerVoice.ACHIRD,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        SpeakerCharacter character2 = new SpeakerCharacter(
            "character-2",
            "project-1",
            1,
            "Bob",
            "Friend",
            SpeakerVoice.ACHERNAR,
            Instant.parse("2026-05-12T10:00:00Z")
        );
        AudiobookSpeechSegment segment = new AudiobookSpeechSegment(
            "segment-1",
            project,
            0,
            "Speech segment 1",
            AudiobookSpeechSegmentReviewStatus.PENDING,
            null,
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            "Hello",
            null,
            character1
        );

        segment.setCharacter(character2);

        assertThat(segment.getCharacter()).isEqualTo(character2);
        assertThat(segment.getCharacterId()).isEqualTo("character-2");
    }
}
