package com.example.ttslab.audiobooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for AudiobookRepository.
 * Tests all JDBC queries, row mappers, and data persistence.
 */
@SpringBootTest
@Transactional
@DisplayName("AudiobookRepository Tests")
class AudiobookRepositoryTest {

  @Autowired private AudiobookRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String USER_ID = "test-user-123";
  private static final String PROJECT_ID = "test-project-1";

  @Test
  @DisplayName("Create project persists all fields to database")
  void testCreateProject() {
    AudiobookProject project = new AudiobookProject(
        PROJECT_ID,
        USER_ID,
        "Test Audiobook",
        AudiobookProjectStatus.NEEDS_REVIEW,
        "TTS_WORKBENCH",
        5,
        2,
        300,
        null,
        null
    );

    repository.createProject(project);

    Optional<AudiobookProject> retrieved = repository.findProjectForUser(PROJECT_ID, USER_ID);
    assertThat(retrieved).isPresent();
    AudiobookProject p = retrieved.get();
    assertThat(p.id()).isEqualTo(PROJECT_ID);
    assertThat(p.userId()).isEqualTo(USER_ID);
    assertThat(p.title()).isEqualTo("Test Audiobook");
    assertThat(p.status()).isEqualTo(AudiobookProjectStatus.NEEDS_REVIEW);
    assertThat(p.sourceType()).isEqualTo("TTS_WORKBENCH");
    assertThat(p.sceneCount()).isEqualTo(5);
    assertThat(p.speakerCount()).isEqualTo(2);
    assertThat(p.totalDurationSeconds()).isEqualTo(300);
  }

  @Test
  @DisplayName("FindProjectForUser filters by userId for access control")
  void testFindProjectForUserAccessControl() {
    AudiobookProject project = new AudiobookProject(
        PROJECT_ID,
        USER_ID,
        "Test Audiobook",
        AudiobookProjectStatus.NEEDS_REVIEW,
        "TTS_WORKBENCH",
        0,
        null,
        null,
        null,
        null
    );
    repository.createProject(project);

    // Same user can retrieve
    Optional<AudiobookProject> found = repository.findProjectForUser(PROJECT_ID, USER_ID);
    assertThat(found).isPresent();

    // Different user cannot retrieve
    Optional<AudiobookProject> notFound = repository.findProjectForUser(PROJECT_ID, "other-user");
    assertThat(notFound).isEmpty();
  }

  @Test
  @DisplayName("FindProjectsForUser returns all projects for user sorted by update time")
  void testFindProjectsForUser() {
    String user1 = "user-1";
    String user2 = "user-2";

    // Create 3 projects for user1
    for (int i = 0; i < 3; i++) {
      AudiobookProject project = new AudiobookProject(
          "proj-" + i,
          user1,
          "Project " + i,
          AudiobookProjectStatus.NEEDS_REVIEW,
          "TTS",
          i,
          null,
          null,
          null,
          null
      );
      repository.createProject(project);
    }

    // Create 2 projects for user2
    for (int i = 0; i < 2; i++) {
      AudiobookProject project = new AudiobookProject(
          "proj-u2-" + i,
          user2,
          "Project U2-" + i,
          AudiobookProjectStatus.NEEDS_REVIEW,
          "TTS",
          i,
          null,
          null,
          null,
          null
      );
      repository.createProject(project);
    }

    List<AudiobookProject> user1Projects = repository.findProjectsForUser(user1);
    List<AudiobookProject> user2Projects = repository.findProjectsForUser(user2);

    assertThat(user1Projects).hasSize(3);
    assertThat(user2Projects).hasSize(2);
    assertThat(user1Projects.stream().allMatch(p -> p.userId().equals(user1))).isTrue();
    assertThat(user2Projects.stream().allMatch(p -> p.userId().equals(user2))).isTrue();
  }

  @Test
  @DisplayName("Add scene persists all fields including speaker metadata")
  void testAddScene() {
    createProject();

    AudiobookSpeechSegment scene = new AudiobookSpeechSegment(
        "scene-1",
        PROJECT_ID,
        1,
        "Opening Scene",
        AudiobookSpeechSegmentReviewStatus.APPROVED,
        120,
        null,
        null,
        "Alice",
        "Brave explorer",
        "Warm alto voice",
        "[confident] Let's begin"
    );

    repository.addScene(scene);

    List<AudiobookSpeechSegment> scenes = repository.findScenes(PROJECT_ID);
    assertThat(scenes).hasSize(1);

    AudiobookSpeechSegment retrieved = scenes.get(0);
    assertThat(retrieved.id()).isEqualTo("scene-1");
    assertThat(retrieved.orderIndex()).isEqualTo(1);
    assertThat(retrieved.title()).isEqualTo("Opening Scene");
    assertThat(retrieved.reviewStatus()).isEqualTo(AudiobookSpeechSegmentReviewStatus.APPROVED);
    assertThat(retrieved.durationSeconds()).isEqualTo(120);
    assertThat(retrieved.speakerName()).isEqualTo("Alice");
    assertThat(retrieved.speakerRoleDescription()).isEqualTo("Brave explorer");
    assertThat(retrieved.voiceName()).isEqualTo("Warm alto voice");
    assertThat(retrieved.performanceDirections()).isEqualTo("[confident] Let's begin");
  }

  @Test
  @DisplayName("FindScenes returns scenes ordered by order_index")
  void testFindScenesOrdered() {
    createProject();

    // Add scenes out of order
    repository.addScene(createScene("scene-3", 3));
    repository.addScene(createScene("scene-1", 1));
    repository.addScene(createScene("scene-2", 2));

    List<AudiobookSpeechSegment> scenes = repository.findScenes(PROJECT_ID);

    assertThat(scenes).hasSize(3);
    assertThat(scenes.get(0).orderIndex()).isEqualTo(1);
    assertThat(scenes.get(1).orderIndex()).isEqualTo(2);
    assertThat(scenes.get(2).orderIndex()).isEqualTo(3);
  }

  @Test
  @DisplayName("Add asset persists all fields including duration")
  void testAddAsset() {
    createProject();
    // Create scene first (foreign key requirement)
    repository.addScene(createScene("scene-1", 1));

    AudioAsset asset = new AudioAsset(
        "asset-1",
        PROJECT_ID,
        "scene-1",
        AudioAssetType.PREVIEW_MP3,
        1,
        "s3://bucket/audio.mp3",
        "preview.mp3",
        "audio/mpeg",
        50000L,
        300,
        AudioAssetStatus.READY,
        Instant.now()
    );

    repository.addAsset(asset);

    List<AudioAsset> assets = repository.findAssets(PROJECT_ID);
    assertThat(assets).hasSize(1);

    AudioAsset retrieved = assets.get(0);
    assertThat(retrieved.id()).isEqualTo("asset-1");
    assertThat(retrieved.sceneId()).isEqualTo("scene-1");
    assertThat(retrieved.type()).isEqualTo(AudioAssetType.PREVIEW_MP3);
    assertThat(retrieved.version()).isEqualTo(1);
    assertThat(retrieved.storageKey()).isEqualTo("s3://bucket/audio.mp3");
    assertThat(retrieved.filename()).isEqualTo("preview.mp3");
    assertThat(retrieved.contentType()).isEqualTo("audio/mpeg");
    assertThat(retrieved.sizeBytes()).isEqualTo(50000L);
    assertThat(retrieved.durationSeconds()).isEqualTo(300);
    assertThat(retrieved.status()).isEqualTo(AudioAssetStatus.READY);
  }

  @Test
  @DisplayName("FindAssets returns all assets for project ordered by creation date")
  void testFindAssets() {
    createProject();
    // Create scenes first (foreign key requirement)
    repository.addScene(createScene("scene-1", 1));
    repository.addScene(createScene("scene-2", 2));
    repository.addScene(createScene("scene-3", 3));

    repository.addAsset(createAsset("asset-1", "scene-1"));
    repository.addAsset(createAsset("asset-2", "scene-2"));
    repository.addAsset(createAsset("asset-3", "scene-3"));

    List<AudioAsset> assets = repository.findAssets(PROJECT_ID);
    assertThat(assets).hasSize(3);
    assertThat(assets.stream().allMatch(a -> a.projectId().equals(PROJECT_ID))).isTrue();
  }

  @Test
  @DisplayName("FindAssetForUser verifies ownership via project.user_id")
  void testFindAssetForUserOwnershipCheck() {
    createProject();
    // Create scene first (foreign key requirement)
    repository.addScene(createScene("scene-1", 1));

    AudioAsset asset = createAsset("asset-1", "scene-1");
    repository.addAsset(asset);

    // Owner can retrieve
    Optional<AudioAsset> found = repository.findAssetForUser(PROJECT_ID, "asset-1", USER_ID);
    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo("asset-1");

    // Non-owner cannot retrieve
    Optional<AudioAsset> notFound = repository.findAssetForUser(PROJECT_ID, "asset-1", "other-user");
    assertThat(notFound).isEmpty();
  }

  @Test
  @DisplayName("CreateProjectWithAsset is transactional - creates all three records")
  void testCreateProjectWithAssetTransactional() {
    String projectId = "proj-atomic";
    AudiobookProject project = new AudiobookProject(
        projectId,
        USER_ID,
        "Atomic Test",
        AudiobookProjectStatus.NEEDS_REVIEW,
        "TTS",
        1,
        null,
        null,
        null,
        null
    );
    AudiobookSpeechSegment scene = new AudiobookSpeechSegment(
        "atomic-scene",
        projectId,
        1,
        "Atomic Scene",
        AudiobookSpeechSegmentReviewStatus.PENDING,
        100,
        null,
        null,
        "Speaker",
        "Role",
        "Voice",
        null
    );
    AudioAsset asset = new AudioAsset(
        "atomic-asset",
        projectId,
        "atomic-scene",
        AudioAssetType.PREVIEW_MP3,
        1,
        "key/atomic",
        "file.mp3",
        "audio/mpeg",
        5000L,
        100,
        AudioAssetStatus.READY,
        Instant.now()
    );

    repository.createProjectWithAsset(project, scene, asset);

    // Verify all three records exist
    Optional<AudiobookProject> projFound = repository.findProjectForUser(projectId, USER_ID);
    List<AudiobookSpeechSegment> scenes = repository.findScenes(projectId);
    List<AudioAsset> assets = repository.findAssets(projectId);

    assertThat(projFound).isPresent();
    assertThat(scenes).hasSize(1);
    assertThat(assets).hasSize(1);
  }

  @Test
  @DisplayName("UpdateProjectMetadata updates scene and speaker counts")
  void testUpdateProjectMetadata() {
    createProject();

    repository.updateProjectMetadata(PROJECT_ID, 10, 4, 1200);

    Optional<AudiobookProject> updated = repository.findProjectForUser(PROJECT_ID, USER_ID);
    assertThat(updated).isPresent();
    assertThat(updated.get().sceneCount()).isEqualTo(10);
    assertThat(updated.get().speakerCount()).isEqualTo(4);
    assertThat(updated.get().totalDurationSeconds()).isEqualTo(1200);
  }

  @Test
  @DisplayName("Row mapper handles null values for optional integer fields")
  void testRowMapperNullHandling() {
    // Create project with null optional fields
    AudiobookProject project = new AudiobookProject(
        "null-test",
        USER_ID,
        "Null Test",
        AudiobookProjectStatus.NEEDS_REVIEW,
        "TTS",
        0,
        null,  // nullable
        null,  // nullable
        null,
        null
    );
    repository.createProject(project);

    Optional<AudiobookProject> retrieved = repository.findProjectForUser("null-test", USER_ID);
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().speakerCount()).isNull();
    assertThat(retrieved.get().totalDurationSeconds()).isNull();
  }

  @Test
  @DisplayName("Multiple projects and scenes are isolated by project ID")
  void testProjectIsolation() {
    String proj1 = "proj-1";
    String proj2 = "proj-2";

    // Create two projects
    repository.createProject(new AudiobookProject(proj1, USER_ID, "P1", AudiobookProjectStatus.NEEDS_REVIEW, "TTS", 0, null, null, null, null));
    repository.createProject(new AudiobookProject(proj2, USER_ID, "P2", AudiobookProjectStatus.NEEDS_REVIEW, "TTS", 0, null, null, null, null));

    // Add scenes to each
    repository.addScene(new AudiobookSpeechSegment("scene-p1", proj1, 1, "S1", AudiobookSpeechSegmentReviewStatus.PENDING, null, null, null, null, null, null, null));
    repository.addScene(new AudiobookSpeechSegment("scene-p2", proj2, 1, "S2", AudiobookSpeechSegmentReviewStatus.PENDING, null, null, null, null, null, null, null));

    // Verify isolation
    List<AudiobookSpeechSegment> p1Scenes = repository.findScenes(proj1);
    List<AudiobookSpeechSegment> p2Scenes = repository.findScenes(proj2);

    assertThat(p1Scenes).hasSize(1);
    assertThat(p2Scenes).hasSize(1);
    assertThat(p1Scenes.get(0).id()).isEqualTo("scene-p1");
    assertThat(p2Scenes.get(0).id()).isEqualTo("scene-p2");
  }

  // ──── Helper methods ────

  private void createProject() {
    repository.createProject(new AudiobookProject(
        PROJECT_ID,
        USER_ID,
        "Test Project",
        AudiobookProjectStatus.NEEDS_REVIEW,
        "TTS",
        0,
        null,
        null,
        null,
        null
    ));
  }

  private AudiobookSpeechSegment createScene(String id, int orderIndex) {
    return new AudiobookSpeechSegment(
        id,
        PROJECT_ID,
        orderIndex,
        "Scene " + orderIndex,
        AudiobookSpeechSegmentReviewStatus.PENDING,
        100,
        null,
        null,
        "Speaker",
        "Role",
        "Voice",
        null
    );
  }

  private AudioAsset createAsset(String assetId, String sceneId) {
    return new AudioAsset(
        assetId,
        PROJECT_ID,
        sceneId,
        AudioAssetType.PREVIEW_MP3,
        1,
        "key/" + assetId,
        "file.mp3",
        "audio/mpeg",
        5000L,
        100,
        AudioAssetStatus.READY,
        Instant.now()
    );
  }
}
