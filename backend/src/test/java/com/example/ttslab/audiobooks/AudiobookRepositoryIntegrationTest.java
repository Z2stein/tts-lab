package com.example.ttslab.audiobooks;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(properties = {
    "AUTH_MODE=mock",
    "ENVIRONMENT=feature",
    "MOCK_USER_ID=user-1",
    "MOCK_USER_EMAIL=user1@example.com",
    "MOCK_USER_NAME=Test User",
    "MOCK_USER_ROLES=USER"
})
@DisplayName("Audiobook Repository Integration Tests (New Schema)")
class AudiobookRepositoryIntegrationTest {
    @Autowired
    AudiobookRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String userId;
    private String projectId;
    private String characterId1;
    private String characterId2;
    private String segmentId1;
    private String segmentId2;
    private String runId;
    private String renderSegmentId;
    private String assetId;

    @BeforeEach
    void setup() {
        userId = "user-1";
        projectId = UUID.randomUUID().toString();
        characterId1 = UUID.randomUUID().toString();
        characterId2 = UUID.randomUUID().toString();
        segmentId1 = UUID.randomUUID().toString();
        segmentId2 = UUID.randomUUID().toString();
        runId = UUID.randomUUID().toString();
        renderSegmentId = UUID.randomUUID().toString();
        assetId = UUID.randomUUID().toString();

        // Clean up test data
        jdbcTemplate.update("DELETE FROM render_segment");
        jdbcTemplate.update("DELETE FROM ai_generation_run");
        jdbcTemplate.update("DELETE FROM audio_asset");
        jdbcTemplate.update("DELETE FROM speech_segment");
        jdbcTemplate.update("DELETE FROM character");
        jdbcTemplate.update("DELETE FROM audiobook_project");
    }

    // ============================================================================
    // Project CRUD Tests
    // ============================================================================

    @Test
    @DisplayName("Create project with all fields")
    void testCreateProject() {
        AudiobookProject project = new AudiobookProject(
            projectId, userId, "The Amber Signal",
            "Once upon a time...", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.DRAFT, 1, Instant.now(), Instant.now()
        );

        repository.createProject(project);

        Optional<AudiobookProject> found = repository.findProjectForUser(projectId, userId);
        assertThat(found).isPresent();
        assertThat(found.get().title()).isEqualTo("The Amber Signal");
        assertThat(found.get().sourceText()).startsWith("Once upon a time");
        assertThat(found.get().languageCode()).isEqualTo("en-US");
        assertThat(found.get().modelName()).isEqualTo("claude-opus");
        assertThat(found.get().audioEncoding()).isEqualTo("mp3");
        assertThat(found.get().status()).isEqualTo(AudiobookProjectStatus.DRAFT);
        assertThat(found.get().revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Find project enforces user ownership")
    void testFindProjectEnforcesUserOwnership() {
        AudiobookProject project = new AudiobookProject(
            projectId, userId, "My Project",
            "Text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.DRAFT, 1, Instant.now(), Instant.now()
        );
        repository.createProject(project);

        Optional<AudiobookProject> found = repository.findProjectForUser(projectId, "other-user");
        assertThat(found).isEmpty();

        Optional<AudiobookProject> ownerFound = repository.findProjectForUser(projectId, userId);
        assertThat(ownerFound).isPresent();
    }

    @Test
    @DisplayName("List projects for user")
    void testListProjectsForUser() {
        AudiobookProject project1 = new AudiobookProject(
            projectId, userId, "Project One",
            "Text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.DRAFT, 1, Instant.now(), Instant.now()
        );
        String projectId2 = UUID.randomUUID().toString();
        AudiobookProject project2 = new AudiobookProject(
            projectId2, userId, "Project Two",
            "Text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.NEEDS_REVIEW, 2, Instant.now(), Instant.now()
        );

        repository.createProject(project1);
        repository.createProject(project2);

        List<AudiobookProject> projects = repository.findProjectsForUser(userId);
        assertThat(projects).hasSize(2);
        assertThat(projects).extracting(AudiobookProject::title)
            .containsExactlyInAnyOrder("Project One", "Project Two");
    }

    @Test
    @DisplayName("Update project status and revision")
    void testUpdateProject() {
        AudiobookProject project = new AudiobookProject(
            projectId, userId, "Project",
            "Text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.DRAFT, 1, Instant.now(), Instant.now()
        );
        repository.createProject(project);

        AudiobookProject updated = new AudiobookProject(
            projectId, userId, "Project",
            "Text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.NEEDS_REVIEW, 2, Instant.now(), Instant.now()
        );
        repository.updateProject(updated);

        Optional<AudiobookProject> found = repository.findProjectForUser(projectId, userId);
        assertThat(found.get().status()).isEqualTo(AudiobookProjectStatus.NEEDS_REVIEW);
        assertThat(found.get().revision()).isEqualTo(2);
    }

    // ============================================================================
    // Character CRUD Tests
    // ============================================================================

    @Test
    @DisplayName("Create and retrieve character")
    void testCreateCharacter() {
        createProject();

        Character character = new Character(
            characterId1, projectId, "Alice", "Protagonist",
            "en-US-Neural2-A", 1, true, Instant.now(), Instant.now()
        );
        repository.createCharacter(character);

        Optional<Character> found = repository.findCharacter(characterId1, projectId);
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Alice");
        assertThat(found.get().roleDescription()).isEqualTo("Protagonist");
        assertThat(found.get().voiceKey()).isEqualTo("en-US-Neural2-A");
        assertThat(found.get().approved()).isTrue();
    }

    @Test
    @DisplayName("List characters for project")
    void testListCharactersForProject() {
        createProject();

        Character char1 = new Character(
            characterId1, projectId, "Alice", "Protagonist",
            "en-US-Neural2-A", 1, true, Instant.now(), Instant.now()
        );
        Character char2 = new Character(
            characterId2, projectId, "Bob", "Antagonist",
            "en-US-Neural2-C", 2, false, Instant.now(), Instant.now()
        );
        repository.createCharacter(char1);
        repository.createCharacter(char2);

        List<Character> characters = repository.findCharactersForProject(projectId);
        assertThat(characters).hasSize(2);
        assertThat(characters).extracting(Character::name)
            .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    @DisplayName("Update character properties")
    void testUpdateCharacter() {
        createProject();

        Character character = new Character(
            characterId1, projectId, "Alice", "Protagonist",
            "en-US-Neural2-A", 1, false, Instant.now(), Instant.now()
        );
        repository.createCharacter(character);

        Character updated = new Character(
            characterId1, projectId, "Alice", "Main Character",
            "en-US-Neural2-B", 1, true, Instant.now(), Instant.now()
        );
        repository.updateCharacter(updated);

        Optional<Character> found = repository.findCharacter(characterId1, projectId);
        assertThat(found.get().roleDescription()).isEqualTo("Main Character");
        assertThat(found.get().voiceKey()).isEqualTo("en-US-Neural2-B");
        assertThat(found.get().approved()).isTrue();
    }

    // ============================================================================
    // Speech Segment CRUD Tests
    // ============================================================================

    @Test
    @DisplayName("Create and retrieve speech segment")
    void testCreateSpeechSegment() {
        createProject();
        Character character = new Character(
            characterId1, projectId, "Alice", "Protagonist",
            "voice-1", 1, false, Instant.now(), Instant.now()
        );
        repository.createCharacter(character);

        SpeechSegment segment = new SpeechSegment(
            segmentId1, projectId, characterId1, 1,
            "The story begins here.", null, false, false,
            Instant.now(), Instant.now()
        );
        repository.createSegment(segment);

        Optional<SpeechSegment> found = repository.findSegment(segmentId1, projectId);
        assertThat(found).isPresent();
        assertThat(found.get().originalText()).isEqualTo("The story begins here.");
        assertThat(found.get().sequenceNo()).isEqualTo(1);
        assertThat(found.get().characterId()).isEqualTo(characterId1);
    }

    @Test
    @DisplayName("List segments for project in order")
    void testListSegmentsForProject() {
        createProject();
        Character character = new Character(
            characterId1, projectId, "Alice", "Protagonist",
            "voice-1", 1, false, Instant.now(), Instant.now()
        );
        repository.createCharacter(character);

        SpeechSegment seg1 = new SpeechSegment(
            segmentId1, projectId, characterId1, 1, "First line", null, false, false,
            Instant.now(), Instant.now()
        );
        String segmentId2Tmp = UUID.randomUUID().toString();
        SpeechSegment seg2 = new SpeechSegment(
            segmentId2Tmp, projectId, characterId1, 2, "Second line", null, false, false,
            Instant.now(), Instant.now()
        );
        repository.createSegment(seg1);
        repository.createSegment(seg2);

        List<SpeechSegment> segments = repository.findSegmentsForProject(projectId);
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).sequenceNo()).isEqualTo(1);
        assertThat(segments.get(1).sequenceNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("Update segment approval and annotations")
    void testUpdateSegment() {
        createProject();
        Character character = new Character(
            characterId1, projectId, "Alice", "Protagonist",
            "voice-1", 1, false, Instant.now(), Instant.now()
        );
        repository.createCharacter(character);

        SpeechSegment segment = new SpeechSegment(
            segmentId1, projectId, characterId1, 1,
            "Original text", null, false, false,
            Instant.now(), Instant.now()
        );
        repository.createSegment(segment);

        SpeechSegment updated = new SpeechSegment(
            segmentId1, projectId, characterId1, 1,
            "Original text", "Annotated text", true, true,
            Instant.now(), Instant.now()
        );
        repository.updateSegment(updated);

        Optional<SpeechSegment> found = repository.findSegment(segmentId1, projectId);
        assertThat(found.get().annotatedText()).isEqualTo("Annotated text");
        assertThat(found.get().edited()).isTrue();
        assertThat(found.get().approved()).isTrue();
    }

    // ============================================================================
    // Generation Run Tests
    // ============================================================================

    @Test
    @DisplayName("Create and retrieve generation run")
    void testCreateGenerationRun() {
        createProject();

        AIGenerationRun run = new AIGenerationRun(
            runId, projectId, RunType.AUDIO_GENERATION,
            RunStatus.RUNNING, "{\"input\": \"data\"}", null, null,
            Instant.now(), null, Instant.now()
        );
        repository.createGenerationRun(run);

        Optional<AIGenerationRun> found = repository.findGenerationRun(runId, projectId);
        assertThat(found).isPresent();
        assertThat(found.get().type()).isEqualTo(RunType.AUDIO_GENERATION);
        assertThat(found.get().status()).isEqualTo(RunStatus.RUNNING);
        assertThat(found.get().requestJson()).isEqualTo("{\"input\": \"data\"}");
    }

    @Test
    @DisplayName("Update generation run status and response")
    void testUpdateGenerationRunStatus() {
        createProject();

        AIGenerationRun run = new AIGenerationRun(
            runId, projectId, RunType.AUDIO_GENERATION,
            RunStatus.RUNNING, "{}", null, null,
            Instant.now(), null, Instant.now()
        );
        repository.createGenerationRun(run);

        repository.updateGenerationRunStatus(
            runId, RunStatus.SUCCEEDED,
            "{\"output\": \"result\"}", null
        );

        Optional<AIGenerationRun> found = repository.findGenerationRun(runId, projectId);
        assertThat(found.get().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(found.get().responseJson()).isEqualTo("{\"output\": \"result\"}");
        assertThat(found.get().completedAt()).isNotNull();
    }

    @Test
    @DisplayName("List generation runs for project")
    void testListGenerationRunsForProject() {
        createProject();

        String runId2 = UUID.randomUUID().toString();
        AIGenerationRun run1 = new AIGenerationRun(
            runId, projectId, RunType.CAST_DISCOVERY,
            RunStatus.SUCCEEDED, "{}", "{}", null,
            Instant.now(), Instant.now(), Instant.now()
        );
        AIGenerationRun run2 = new AIGenerationRun(
            runId2, projectId, RunType.AUDIO_GENERATION,
            RunStatus.RUNNING, "{}", null, null,
            Instant.now(), null, Instant.now()
        );
        repository.createGenerationRun(run1);
        repository.createGenerationRun(run2);

        List<AIGenerationRun> runs = repository.findGenerationRunsForProject(projectId);
        assertThat(runs).hasSize(2);
        assertThat(runs).extracting(AIGenerationRun::type)
            .containsExactlyInAnyOrder(RunType.CAST_DISCOVERY, RunType.AUDIO_GENERATION);
    }

    // ============================================================================
    // Render Segment Tests
    // ============================================================================

    @Test
    @DisplayName("Create and retrieve render segment")
    void testCreateRenderSegment() {
        createProjectWithGenerationRun();

        RenderSegment render = new RenderSegment(
            renderSegmentId, runId, segmentId1, "Text to render",
            "{\"provider\": \"google\"}", RunStatus.RUNNING, null,
            Instant.now(), Instant.now(), null
        );
        repository.createRenderSegment(render);

        Optional<RenderSegment> found = repository.findRenderSegment(renderSegmentId);
        assertThat(found).isPresent();
        assertThat(found.get().speechSegmentId()).isEqualTo(segmentId1);
        assertThat(found.get().status()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    @DisplayName("Update render segment status and asset")
    void testUpdateRenderSegmentStatus() {
        createProjectWithGenerationRun();

        RenderSegment render = new RenderSegment(
            renderSegmentId, runId, segmentId1, "Text",
            "{}", RunStatus.RUNNING, null,
            Instant.now(), Instant.now(), null
        );
        repository.createRenderSegment(render);

        repository.updateRenderSegmentStatus(
            renderSegmentId, RunStatus.SUCCEEDED, assetId
        );

        Optional<RenderSegment> found = repository.findRenderSegment(renderSegmentId);
        assertThat(found.get().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(found.get().audioAssetId()).isEqualTo(assetId);
        assertThat(found.get().completedAt()).isNotNull();
    }

    @Test
    @DisplayName("List render segments for generation run")
    void testListRenderSegmentsForRun() {
        createProjectWithGenerationRun();

        String renderId2 = UUID.randomUUID().toString();
        RenderSegment render1 = new RenderSegment(
            renderSegmentId, runId, segmentId1, "Text 1",
            "{}", RunStatus.SUCCEEDED, null,
            Instant.now(), Instant.now(), Instant.now()
        );
        RenderSegment render2 = new RenderSegment(
            renderId2, runId, segmentId1, "Text 2",
            "{}", RunStatus.SUCCEEDED, null,
            Instant.now(), Instant.now(), Instant.now()
        );
        repository.createRenderSegment(render1);
        repository.createRenderSegment(render2);

        List<RenderSegment> renders = repository.findRenderSegmentsForRun(runId);
        assertThat(renders).hasSize(2);
        assertThat(renders).extracting(RenderSegment::speechSegmentId)
            .containsExactlyInAnyOrder(segmentId1, segmentId1);
    }

    // ============================================================================
    // Audio Asset Tests
    // ============================================================================

    @Test
    @DisplayName("Create and retrieve audio asset")
    void testCreateAudioAsset() {
        createProjectWithGenerationRun();

        AudioAsset asset = new AudioAsset(
            assetId, projectId, runId, AudioAssetType.VOICE_PREVIEW,
            "sample.mp3", "s3://bucket/key.mp3", "audio/mpeg",
            120000L, 5000000L, Instant.now()
        );
        repository.createAudioAsset(asset);

        Optional<AudioAsset> found = repository.findAssetForUser(projectId, assetId, userId);
        assertThat(found).isPresent();
        assertThat(found.get().fileName()).isEqualTo("sample.mp3");
        assertThat(found.get().type()).isEqualTo(AudioAssetType.VOICE_PREVIEW);
        assertThat(found.get().durationMs()).isEqualTo(120000L);
    }

    @Test
    @DisplayName("List assets for project")
    void testListAssetsForProject() {
        createProjectWithGenerationRun();

        String assetId2 = UUID.randomUUID().toString();
        AudioAsset asset1 = new AudioAsset(
            assetId, projectId, runId, AudioAssetType.SEGMENT_AUDIO,
            "segment1.mp3", "s3://key1", "audio/mpeg",
            60000L, 3000000L, Instant.now()
        );
        AudioAsset asset2 = new AudioAsset(
            assetId2, projectId, null, AudioAssetType.FULL_AUDIOBOOK,
            "full.mp3", "s3://key2", "audio/mpeg",
            300000L, 15000000L, Instant.now()
        );
        repository.createAudioAsset(asset1);
        repository.createAudioAsset(asset2);

        List<AudioAsset> assets = repository.findAssetsForProject(projectId);
        assertThat(assets).hasSize(2);
        assertThat(assets).extracting(AudioAsset::type)
            .containsExactlyInAnyOrder(AudioAssetType.SEGMENT_AUDIO, AudioAssetType.FULL_AUDIOBOOK);
    }

    // ============================================================================
    // Transactional Bulk Operations Tests
    // ============================================================================

    @Test
    @DisplayName("Create project with characters and segments atomically")
    void testCreateProjectWithCharactersAndSegmentsAtomically() {
        Instant now = Instant.now();
        AudiobookProject project = new AudiobookProject(
            projectId, userId, "Bulk Project",
            "Text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.DRAFT, 1, now, now
        );

        Character char1 = new Character(
            characterId1, projectId, "Alice", "Protagonist",
            "voice-1", 1, false, now, now
        );
        Character char2 = new Character(
            characterId2, projectId, "Bob", "Antagonist",
            "voice-2", 2, false, now, now
        );

        SpeechSegment seg1 = new SpeechSegment(
            segmentId1, projectId, characterId1, 1,
            "First line", null, false, false, now, now
        );

        repository.createProjectWithCharactersAndSegments(
            project, List.of(char1, char2), List.of(seg1)
        );

        Optional<AudiobookProject> foundProject = repository.findProjectForUser(projectId, userId);
        assertThat(foundProject).isPresent();

        List<Character> foundChars = repository.findCharactersForProject(projectId);
        assertThat(foundChars).hasSize(2);

        List<SpeechSegment> foundSegs = repository.findSegmentsForProject(projectId);
        assertThat(foundSegs).hasSize(1);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private void createProject() {
        AudiobookProject project = new AudiobookProject(
            projectId, userId, "Test Project",
            "Test text", "en-US", "claude-opus", "mp3",
            AudiobookProjectStatus.DRAFT, 1, Instant.now(), Instant.now()
        );
        repository.createProject(project);
    }

    private void createProjectWithCharacter() {
        createProject();

        Character character = new Character(
            characterId1, projectId, "TestChar", "Role",
            "voice-key", 1, false, Instant.now(), Instant.now()
        );
        repository.createCharacter(character);
    }

    private void createProjectWithGenerationRun() {
        createProject();
        Character character = new Character(
            characterId1, projectId, "TestChar", "Role",
            "voice-key", 1, false, Instant.now(), Instant.now()
        );
        repository.createCharacter(character);

        SpeechSegment segment = new SpeechSegment(
            segmentId1, projectId, characterId1, 1,
            "Test text", null, false, false,
            Instant.now(), Instant.now()
        );
        repository.createSegment(segment);

        AIGenerationRun run = new AIGenerationRun(
            runId, projectId, RunType.AUDIO_GENERATION,
            RunStatus.RUNNING, "{}", null, null,
            Instant.now(), null, Instant.now()
        );
        repository.createGenerationRun(run);
    }
}
