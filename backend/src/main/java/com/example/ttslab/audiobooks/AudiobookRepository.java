package com.example.ttslab.audiobooks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AudiobookRepository {
    private final JdbcTemplate jdbcTemplate;

    public AudiobookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AudiobookProject> findProjectsForUser(String userId) {
        return jdbcTemplate.query("""
            SELECT id, user_id, title, status, source_type, scene_count, speaker_count, total_duration_seconds, created_at, updated_at
            FROM audiobook_project
            WHERE user_id = ?
            ORDER BY updated_at DESC, created_at DESC
            """, projectMapper(), userId);
    }

    public Optional<AudiobookProject> findProjectForUser(String projectId, String userId) {
        return jdbcTemplate.query("""
            SELECT id, user_id, title, status, source_type, scene_count, speaker_count, total_duration_seconds, created_at, updated_at
            FROM audiobook_project
            WHERE id = ? AND user_id = ?
            """, projectMapper(), projectId, userId).stream().findFirst();
    }

    public List<AudiobookScene> findScenes(String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, order_index, title, review_status, duration_seconds, created_at, updated_at
            FROM audiobook_scene
            WHERE project_id = ?
            ORDER BY order_index ASC
            """, sceneMapper(), projectId);
    }

    public List<AudioAsset> findAssets(String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, scene_id, type, version, storage_key, filename, content_type, size_bytes, duration_seconds, status, created_at
            FROM audio_asset
            WHERE project_id = ?
            ORDER BY created_at DESC
            """, assetMapper(), projectId);
    }

    public Optional<AudioAsset> findAssetForUser(String projectId, String assetId, String userId) {
        return jdbcTemplate.query("""
            SELECT aa.id, aa.project_id, aa.scene_id, aa.type, aa.version, aa.storage_key, aa.filename, aa.content_type,
                   aa.size_bytes, aa.duration_seconds, aa.status, aa.created_at
            FROM audio_asset aa
            INNER JOIN audiobook_project ap ON ap.id = aa.project_id
            WHERE aa.project_id = ? AND aa.id = ? AND ap.user_id = ?
            """, assetMapper(), projectId, assetId, userId).stream().findFirst();
    }

    @Transactional
    public void createProjectWithAsset(AudiobookProject project, AudiobookScene scene, AudioAsset asset) {
        jdbcTemplate.update("""
            INSERT INTO audiobook_project (id, user_id, title, status, source_type, scene_count, speaker_count, total_duration_seconds, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            project.id(), project.userId(), project.title(), project.status().name(), project.sourceType(), project.sceneCount(),
            project.speakerCount(), project.totalDurationSeconds());
        jdbcTemplate.update("""
            INSERT INTO audiobook_scene (id, project_id, order_index, title, review_status, duration_seconds, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            scene.id(), scene.projectId(), scene.orderIndex(), scene.title(), scene.reviewStatus().name(), scene.durationSeconds());
        jdbcTemplate.update("""
            INSERT INTO audio_asset (id, project_id, scene_id, type, version, storage_key, filename, content_type, size_bytes, duration_seconds, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            asset.id(), asset.projectId(), asset.sceneId(), asset.type().name(), asset.version(), asset.storageKey(), asset.filename(),
            asset.contentType(), asset.sizeBytes(), asset.durationSeconds(), asset.status().name());
    }

    private RowMapper<AudiobookProject> projectMapper() {
        return (rs, rowNum) -> new AudiobookProject(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("title"),
            AudiobookProjectStatus.valueOf(rs.getString("status")),
            rs.getString("source_type"),
            rs.getInt("scene_count"),
            integer(rs, "speaker_count"),
            integer(rs, "total_duration_seconds"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private RowMapper<AudiobookScene> sceneMapper() {
        return (rs, rowNum) -> new AudiobookScene(
            rs.getString("id"),
            rs.getString("project_id"),
            rs.getInt("order_index"),
            rs.getString("title"),
            AudiobookSceneReviewStatus.valueOf(rs.getString("review_status")),
            integer(rs, "duration_seconds"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private RowMapper<AudioAsset> assetMapper() {
        return (rs, rowNum) -> new AudioAsset(
            rs.getString("id"),
            rs.getString("project_id"),
            rs.getString("scene_id"),
            AudioAssetType.valueOf(rs.getString("type")),
            rs.getInt("version"),
            rs.getString("storage_key"),
            rs.getString("filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            integer(rs, "duration_seconds"),
            AudioAssetStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Integer integer(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }
}
