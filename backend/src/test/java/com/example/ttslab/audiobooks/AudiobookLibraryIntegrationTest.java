package com.example.ttslab.audiobooks;

import com.example.ttslab.audiobooks.dto.AudiobookDetailResponse;
import com.example.ttslab.audiobooks.dto.AudiobookSummaryResponse;
import com.example.ttslab.audiobooks.model.*;
import com.example.ttslab.audiobooks.repository.AudiobookRepository;
import com.example.ttslab.error.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(properties = {
    "AUTH_MODE=mock",
    "ENVIRONMENT=feature",
    "MOCK_USER_ID=user-1",
    "MOCK_USER_EMAIL=user1@example.com",
    "MOCK_USER_NAME=Test User One",
    "MOCK_USER_ROLES=USER"
})
@AutoConfigureMockMvc
@DisplayName("Audiobook Library API Integration Tests")
class AudiobookLibraryIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    AudiobookRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    // Test IDs for fixtures
    private String projectA;
    private String projectB;
    private String projectC;
    private String projectD;
    private String sceneA1;
    private String sceneB1;
    private String assetA1;
    private String assetA2;
    private String assetA3;
    private String assetA4;
    private String assetB1;
    private String assetB2;
    private String assetB3;
    private String assetB4;
    private String assetC1;

    @BeforeEach
    void setupFixtures() {
        // Clean up any previous test data for user-1 and user-2 to ensure test isolation
        jdbcTemplate.update("DELETE FROM audio_asset WHERE project_id IN (SELECT id FROM audiobook_project WHERE user_id IN (?, ?))", "user-1", "user-2");
        jdbcTemplate.update("DELETE FROM audiobook_scene WHERE project_id IN (SELECT id FROM audiobook_project WHERE user_id IN (?, ?))", "user-1", "user-2");
        jdbcTemplate.update("DELETE FROM audiobook_project WHERE user_id IN (?, ?)", "user-1", "user-2");

        // Generate IDs for all fixtures
        projectA = UUID.randomUUID().toString();
        projectB = UUID.randomUUID().toString();
        projectC = UUID.randomUUID().toString();
        projectD = UUID.randomUUID().toString();
        sceneA1 = UUID.randomUUID().toString();
        sceneB1 = UUID.randomUUID().toString();
        assetA1 = UUID.randomUUID().toString();
        assetA2 = UUID.randomUUID().toString();
        assetA3 = UUID.randomUUID().toString();
        assetA4 = UUID.randomUUID().toString();
        assetB1 = UUID.randomUUID().toString();
        assetB2 = UUID.randomUUID().toString();
        assetB3 = UUID.randomUUID().toString();
        assetB4 = UUID.randomUUID().toString();
        assetC1 = UUID.randomUUID().toString();

        // Fixture A: Basic Project with Mixed Asset States (user-1)
        createFixtureA();

        // Fixture B: Multi-Speaker Project (user-1)
        createFixtureB();

        // Fixture C: User Isolation Project (user-2)
        createFixtureC();

        // Fixture D: Empty Project (user-1)
        createFixtureD();
    }

    private void createFixtureA() {
        // Project: "The Amber Signal"
        repository.createProject(new AudiobookProject(
            projectA, "user-1", "The Amber Signal", AudiobookProjectStatus.DRAFT,
            "TTS_WORKBENCH", 0, null, null, null, null
        ));

        // Scene with metadata
        repository.addScene(new AudiobookSpeechSegment(
            sceneA1, projectA, 1, "Opening Scene", AudiobookSpeechSegmentReviewStatus.PENDING,
            30, null, null, "narrator", "Main narrator", null, null
        ));

        // 2 READY assets (15 + 30 seconds)
        repository.addAsset(new AudioAsset(
            assetA1, projectA, sceneA1, AudioAssetType.PREVIEW_MP3, 1, "key-1",
            "segment-1-narrator.mp3", "audio/mpeg", 100000, 15, AudioAssetStatus.READY, null
        ));
        repository.addAsset(new AudioAsset(
            assetA2, projectA, sceneA1, AudioAssetType.PREVIEW_MP3, 1, "key-2",
            "segment-2-mara.mp3", "audio/mpeg", 200000, 30, AudioAssetStatus.READY, null
        ));

        // 1 GENERATING asset (should be excluded from counts)
        repository.addAsset(new AudioAsset(
            assetA3, projectA, sceneA1, AudioAssetType.PREVIEW_MP3, 1, "key-3",
            "segment-3-unknown.mp3", "audio/mpeg", 150000, 25, AudioAssetStatus.GENERATING, null
        ));

        // 1 FAILED asset (should be excluded from counts)
        repository.addAsset(new AudioAsset(
            assetA4, projectA, sceneA1, AudioAssetType.PREVIEW_MP3, 1, "key-4",
            "segment-4-failed.mp3", "audio/mpeg", 100000, null, AudioAssetStatus.FAILED, null
        ));
    }

    private void createFixtureB() {
        // Project: "Voices Unbound"
        repository.createProject(new AudiobookProject(
            projectB, "user-1", "Voices Unbound", AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH", 0, null, null, null, null
        ));

        repository.addScene(new AudiobookSpeechSegment(
            sceneB1, projectB, 1, "Multi-Speaker Scene", AudiobookSpeechSegmentReviewStatus.PENDING,
            55, null, null, null, null, null, null
        ));

        // 4 READY assets with distinct speakers extracted from filenames
        repository.addAsset(new AudioAsset(
            assetB1, projectB, sceneB1, AudioAssetType.PREVIEW_MP3, 1, "key-b1",
            "segment-1-narrator.mp3", "audio/mpeg", 150000, 15, AudioAssetStatus.READY, null
        ));
        repository.addAsset(new AudioAsset(
            assetB2, projectB, sceneB1, AudioAssetType.PREVIEW_MP3, 1, "key-b2",
            "segment-2-mara.mp3", "audio/mpeg", 120000, 12, AudioAssetStatus.READY, null
        ));
        repository.addAsset(new AudioAsset(
            assetB3, projectB, sceneB1, AudioAssetType.PREVIEW_MP3, 1, "key-b3",
            "segment-3-narrator.mp3", "audio/mpeg", 180000, 18, AudioAssetStatus.READY, null
        ));
        repository.addAsset(new AudioAsset(
            assetB4, projectB, sceneB1, AudioAssetType.PREVIEW_MP3, 1, "key-b4",
            "segment-4-alex.mp3", "audio/mpeg", 100000, 10, AudioAssetStatus.READY, null
        ));
    }

    private void createFixtureC() {
        // Project: "Private Audiobook" owned by user-2 (user-1 should not see this)
        String sceneC1 = UUID.randomUUID().toString();
        repository.createProject(new AudiobookProject(
            projectC, "user-2", "Private Audiobook", AudiobookProjectStatus.APPROVED,
            "TTS_WORKBENCH", 0, null, null, null, null
        ));

        repository.addScene(new AudiobookSpeechSegment(
            sceneC1, projectC, 1, "Private Scene", AudiobookSpeechSegmentReviewStatus.PENDING,
            20, null, null, "james", null, null, null
        ));

        repository.addAsset(new AudioAsset(
            assetC1, projectC, sceneC1, AudioAssetType.PREVIEW_MP3, 1, "key-c1",
            "segment-1-james.mp3", "audio/mpeg", 100000, 20, AudioAssetStatus.READY, null
        ));
    }

    private void createFixtureD() {
        // Project: "Empty Draft" with no scenes or assets
        repository.createProject(new AudiobookProject(
            projectD, "user-1", "Empty Draft", AudiobookProjectStatus.NEEDS_REVIEW,
            "TTS_WORKBENCH", 0, null, null, null, null
        ));
    }

    // ============================================================================
    // GROUP 1: Happy Path Tests (2 tests)
    // ============================================================================

    @Test
    @DisplayName("List endpoint returns current user's audiobooks with calculated metadata")
    void testListEndpointReturnsCurrentUsersAudiobooksWithCalculatedMetadata() throws Exception {
        AudiobookSummaryResponse response = getListResponse();

        // Verify all three projects are present
        assertThat(response.items()).hasSize(3);
        assertThat(response.items())
            .extracting(AudiobookSummaryResponse.AudiobookSummaryItem::title)
            .containsExactlyInAnyOrder("The Amber Signal", "Voices Unbound", "Empty Draft");

        // Verify correct metadata: scene count
        assertThat(response.items())
            .extracting(AudiobookSummaryResponse.AudiobookSummaryItem::sceneCount)
            .containsExactlyInAnyOrder(2, 4, 0);

        // Verify correct metadata: speaker count
        assertThat(response.items())
            .extracting(AudiobookSummaryResponse.AudiobookSummaryItem::speakerCount)
            .containsExactlyInAnyOrder(2, 3, 0);

        // Verify correct metadata: total duration
        assertThat(response.items())
            .extracting(AudiobookSummaryResponse.AudiobookSummaryItem::totalDurationSeconds)
            .containsExactlyInAnyOrder(45, 55, 0);
    }

    @Test
    @DisplayName("Detail endpoint returns complete project with scenes and assets")
    void testDetailEndpointReturnsCompleteProjectWithMetadata() throws Exception {
        AudiobookDetailResponse response = getDetailResponse(projectB);

        assertThat(response.id()).isEqualTo(projectB);
        assertThat(response.title()).isEqualTo("Voices Unbound");
        assertThat(response.status()).isEqualTo(AudiobookProjectStatus.NEEDS_REVIEW);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.scenes()).hasSize(1);
        assertThat(response.scenes().get(0).title()).isEqualTo("Multi-Speaker Scene");
        assertThat(response.audioAssets()).hasSize(4);
    }

    // ============================================================================
    // GROUP 2: Metadata Calculation Tests (3 tests)
    // ============================================================================

    @Test
    @DisplayName("List calculates scene count from READY assets only")
    void testListCalculatesSceneCountFromReadyAssetsOnly() throws Exception {
        AudiobookSummaryResponse response = getListResponse();

        var amberSignal = response.items().stream()
            .filter(item -> item.title().equals("The Amber Signal"))
            .findFirst()
            .orElseThrow();

        // Only 2 READY assets in Fixture A (GENERATING and FAILED excluded)
        assertThat(amberSignal.sceneCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("List calculates speaker count by extracting and deduplicating from filenames")
    void testListCalculatesSpeakerCountByExtractingFromFilenames() throws Exception {
        AudiobookSummaryResponse response = getListResponse();

        var voicesUnbound = response.items().stream()
            .filter(item -> item.title().equals("Voices Unbound"))
            .findFirst()
            .orElseThrow();

        // Fixture B has 4 assets with speakers: narrator, mara, narrator (duplicate), alex
        // Expected: 3 unique speakers (deduplication)
        assertThat(voicesUnbound.speakerCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("List calculates total duration from READY assets only")
    void testListCalculatesTotalDurationFromReadyAssets() throws Exception {
        AudiobookSummaryResponse response = getListResponse();

        var amberSignal = response.items().stream()
            .filter(item -> item.title().equals("The Amber Signal"))
            .findFirst()
            .orElseThrow();

        // Fixture A: 15 + 30 = 45 seconds (GENERATING and FAILED excluded)
        assertThat(amberSignal.totalDurationSeconds()).isEqualTo(45);
    }

    // ============================================================================
    // GROUP 3: Speaker Extraction Tests (2 tests)
    // ============================================================================

    @Test
    @DisplayName("Metadata calculator correctly extracts and deduplicates speakers from filenames")
    void testDetailExtractsSpeakerNamesFromAssetFilenames() throws Exception {
        List<AudioAsset> assets = repository.findAssets(projectB);
        assertThat(assets).hasSize(4);

        List<String> filenames = assets.stream().map(AudioAsset::getFilename).toList();
        assertThat(filenames).containsAll(List.of(
            "segment-1-narrator.mp3",
            "segment-2-mara.mp3",
            "segment-3-narrator.mp3",
            "segment-4-alex.mp3"
        ));

        // Verify list endpoint calculates speaker count correctly (narrator, mara, alex deduplicated = 3)
        AudiobookSummaryResponse response = getListResponse();
        var voicesUnbound = response.items().stream()
            .filter(item -> item.title().equals("Voices Unbound"))
            .findFirst()
            .orElseThrow();
        assertThat(voicesUnbound.speakerCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Metadata calculation handles null/missing duration gracefully")
    void testMetadataCalculationHandlesMissingDurationGracefully() throws Exception {
        AudiobookSummaryResponse response = getListResponse();
        var amberSignal = response.items().stream()
            .filter(item -> item.title().equals("The Amber Signal"))
            .findFirst()
            .orElseThrow();

        // Fixture A has a FAILED asset with null duration - should be excluded anyway
        assertThat(amberSignal.totalDurationSeconds()).isEqualTo(45);

        // Verify direct calculation handles null durations correctly
        int duration = repository.findAssets(projectA).stream()
            .filter(a -> a.getStatus() == AudioAssetStatus.READY)
            .mapToInt(a -> a.getDurationSeconds() != null ? a.getDurationSeconds() : 0)
            .sum();
        assertThat(duration).isEqualTo(45);
    }

    // ============================================================================
    // GROUP 4: User Isolation Tests (3 tests)
    // ============================================================================

    @Test
    @DisplayName("List endpoint only returns current user's projects")
    void testListEndpointOnlyReturnsCurrentUsersProjects() throws Exception {
        AudiobookSummaryResponse response = getListResponse();

        // Current user is "user-1", should see exactly 3 projects: A, B, D (NOT C, which belongs to user-2)
        assertThat(response.items()).hasSize(3);
        assertThat(response.items())
            .extracting(AudiobookSummaryResponse.AudiobookSummaryItem::title)
            .containsExactlyInAnyOrder("The Amber Signal", "Voices Unbound", "Empty Draft");

        // Verify each item has required fields
        assertThat(response.items())
            .allMatch(item -> item.id() != null && !item.id().isBlank())
            .allMatch(item -> item.title() != null && !item.title().isBlank());

        // Verify database state: user-1 has exactly 3 projects
        List<AudiobookProject> user1Projects = queryProjectsByUser("user-1");
        assertThat(user1Projects).hasSize(3);
        assertThat(user1Projects)
            .extracting(AudiobookProject::getTitle)
            .containsExactlyInAnyOrder("The Amber Signal", "Voices Unbound", "Empty Draft");

        // Verify database state: user-2 has 1 project that should NOT be visible to user-1
        List<AudiobookProject> user2Projects = queryProjectsByUser("user-2");
        assertThat(user2Projects).hasSize(1);
        assertThat(user2Projects.get(0).getId()).isEqualTo(projectC);
        assertThat(user2Projects.get(0).getTitle()).isEqualTo("Private Audiobook");
    }

    @Test
    @DisplayName("Detail endpoint rejects 404 for another user's project")
    void testDetailEndpointRejects404ForAnotherUsersProject() throws Exception {
        // Try to access project C (owned by user-2) as user-1
        var result = mockMvc.perform(get("/api/audiobooks/" + projectC))
            .andExpect(status().isNotFound())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        var errorResponse = objectMapper.readValue(responseBody, ApiErrorResponse.class);
        assertThat(errorResponse.code()).isEqualTo("AUDIOBOOK_NOT_FOUND");
    }

    @Test
    @DisplayName("User isolation enforced at repository level (SQL WHERE clause)")
    void testUserIsolationEnforcedAtRepositoryLevel() {
        var projectForUser1 = repository.findProjectForUser(projectC, "user-1");
        var projectForUser2 = repository.findProjectForUser(projectC, "user-2");

        assertThat(projectForUser1).isEmpty();  // user-1 cannot see project C
        assertThat(projectForUser2).isPresent();  // user-2 can see their own project
    }

    // ============================================================================
    // GROUP 5: Not Found / Error Scenarios (2 tests)
    // ============================================================================

    @Test
    @DisplayName("Detail endpoint returns 404 for non-existent project")
    void testDetailEndpointReturns404ForNonExistentProject() throws Exception {
        String nonExistentId = UUID.randomUUID().toString();
        var result = mockMvc.perform(get("/api/audiobooks/" + nonExistentId))
            .andExpect(status().isNotFound())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        var errorResponse = objectMapper.readValue(responseBody, ApiErrorResponse.class);
        assertThat(errorResponse.code()).isEqualTo("AUDIOBOOK_NOT_FOUND");
    }

    @Test
    @DisplayName("List endpoint returns empty array for user with no projects")
    void testListEndpointReturnsEmptyArrayForUserWithNoProjects() throws Exception {
        // Delete all projects for user-1 to test empty case
        jdbcTemplate.update("DELETE FROM audio_asset WHERE project_id IN (?, ?, ?)", projectA, projectB, projectD);
        jdbcTemplate.update("DELETE FROM audiobook_scene WHERE project_id IN (?, ?, ?)", projectA, projectB, projectD);
        jdbcTemplate.update("DELETE FROM audiobook_project WHERE user_id = ?", "user-1");

        AudiobookSummaryResponse response = getListResponse();
        assertThat(response.items()).isEmpty();
    }

    // ============================================================================
    // GROUP 6: Asset Status Filtering (2 tests)
    // ============================================================================

    @Test
    @DisplayName("Asset status filtering excludes GENERATING and FAILED assets from metadata")
    void testAssetStatusFilteringExcludesNonReadyAssets() throws Exception {
        List<AudioAsset> allAssets = repository.findAssets(projectA);
        long readyCount = allAssets.stream()
            .filter(a -> a.getStatus() == AudioAssetStatus.READY)
            .count();

        AudiobookSummaryResponse response = getListResponse();
        var amberSignal = response.items().stream()
            .filter(item -> item.title().equals("The Amber Signal"))
            .findFirst()
            .orElseThrow();

        assertThat(amberSignal.sceneCount()).isEqualTo((int) readyCount);
        assertThat(amberSignal.audioAssets()).isNotNull();
    }

    @Test
    @DisplayName("Metadata reflects only READY asset metrics, not GENERATING or FAILED")
    void testMetadataReflectsOnlyReadyAssetMetrics() throws Exception {
        // Create a test scenario: GENERATING asset with much larger duration should not affect totals
        String extraAssetId = UUID.randomUUID().toString();
        repository.addAsset(new AudioAsset(
            extraAssetId, projectA, sceneA1, AudioAssetType.PREVIEW_MP3, 1, "key-extra",
            "segment-99-unknown.mp3", "audio/mpeg", 500000, 500, AudioAssetStatus.GENERATING, null
        ));

        AudiobookSummaryResponse response = getListResponse();
        var amberSignal = response.items().stream()
            .filter(item -> item.title().equals("The Amber Signal"))
            .findFirst()
            .orElseThrow();

        // Total duration should still be 45 (15 + 30), not 545 (15 + 30 + 500)
        assertThat(amberSignal.totalDurationSeconds()).isEqualTo(45);
    }

    // ============================================================================
    // GROUP 7: Database State Verification (1 test)
    // ============================================================================

    @Test
    @DisplayName("Project metadata is not persisted in database for list endpoint")
    void testProjectMetadataIsNotPersistedInDatabaseForList() throws Exception {
        // Verify that the database columns are NULL/0, not persisted
        Integer sceneCount = jdbcTemplate.queryForObject(
            "SELECT scene_count FROM audiobook_project WHERE id = ?", Integer.class, projectB
        );
        Integer speakerCount = jdbcTemplate.queryForObject(
            "SELECT speaker_count FROM audiobook_project WHERE id = ?", Integer.class, projectB
        );
        Integer totalDuration = jdbcTemplate.queryForObject(
            "SELECT total_duration_seconds FROM audiobook_project WHERE id = ?", Integer.class, projectB
        );

        // All should be NULL or 0 (not persisted)
        assertThat(sceneCount).isZero();
        assertThat(speakerCount).isNull();
        assertThat(totalDuration).isNull();

        // But the API response should still return calculated values
        AudiobookSummaryResponse response = getListResponse();
        var voicesUnbound = response.items().stream()
            .filter(item -> item.title().equals("Voices Unbound"))
            .findFirst()
            .orElseThrow();

        assertThat(voicesUnbound.sceneCount()).isEqualTo(4);
        assertThat(voicesUnbound.speakerCount()).isEqualTo(3);
        assertThat(voicesUnbound.totalDurationSeconds()).isEqualTo(55);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private AudiobookSummaryResponse getListResponse() throws Exception {
        var result = mockMvc.perform(get("/api/audiobooks"))
            .andExpect(status().isOk())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readValue(responseBody, AudiobookSummaryResponse.class);
    }

    private AudiobookDetailResponse getDetailResponse(String projectId) throws Exception {
        var result = mockMvc.perform(get("/api/audiobooks/" + projectId))
            .andExpect(status().isOk())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        return objectMapper.readValue(responseBody, AudiobookDetailResponse.class);
    }

    private List<AudiobookProject> queryProjectsByUser(String userId) {
        return jdbcTemplate.query(
            "SELECT id, user_id, title, status, source_type, scene_count, speaker_count, total_duration_seconds, created_at, updated_at FROM audiobook_project WHERE user_id = ?",
            (rs, rowNum) -> new AudiobookProject(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("title"),
                AudiobookProjectStatus.valueOf(rs.getString("status")),
                rs.getString("source_type"),
                rs.getInt("scene_count"),
                null,
                null,
                null,
                null
            ),
            userId
        );
    }
}
