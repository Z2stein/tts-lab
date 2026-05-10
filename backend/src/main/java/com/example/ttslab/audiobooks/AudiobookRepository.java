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

    // ==================== Project Management ====================

    public List<AudiobookProject> findProjectsForUser(String userId) {
        return jdbcTemplate.query("""
            SELECT id, user_id, title, source_text, language_code, model_name, audio_encoding, status, revision, created_at, updated_at
            FROM audiobook_project
            WHERE user_id = ?
            ORDER BY updated_at DESC, created_at DESC
            """, projectMapper(), userId);
    }

    public Optional<AudiobookProject> findProjectForUser(String projectId, String userId) {
        return jdbcTemplate.query("""
            SELECT id, user_id, title, source_text, language_code, model_name, audio_encoding, status, revision, created_at, updated_at
            FROM audiobook_project
            WHERE id = ? AND user_id = ?
            """, projectMapper(), projectId, userId).stream().findFirst();
    }

    public void createProject(AudiobookProject project) {
        jdbcTemplate.update("""
            INSERT INTO audiobook_project (id, user_id, title, source_text, language_code, model_name, audio_encoding, status, revision, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            project.id(), project.userId(), project.title(), project.sourceText(), project.languageCode(),
            project.modelName(), project.audioEncoding(), project.status().name(), project.revision());
    }

    public void updateProject(AudiobookProject project) {
        jdbcTemplate.update("""
            UPDATE audiobook_project
            SET title = ?, source_text = ?, language_code = ?, model_name = ?, audio_encoding = ?, status = ?, revision = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            project.title(), project.sourceText(), project.languageCode(), project.modelName(), project.audioEncoding(),
            project.status().name(), project.revision(), project.id());
    }

    // ==================== Character Management ====================

    public List<Character> findCharactersForProject(String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, name, role_description, voice_key, sort_order, approved, created_at, updated_at
            FROM character
            WHERE project_id = ?
            ORDER BY sort_order ASC
            """, characterMapper(), projectId);
    }

    public Optional<Character> findCharacter(String characterId, String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, name, role_description, voice_key, sort_order, approved, created_at, updated_at
            FROM character
            WHERE id = ? AND project_id = ?
            """, characterMapper(), characterId, projectId).stream().findFirst();
    }

    public void createCharacter(Character character) {
        jdbcTemplate.update("""
            INSERT INTO character (id, project_id, name, role_description, voice_key, sort_order, approved, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            character.id(), character.projectId(), character.name(), character.roleDescription(),
            character.voiceKey(), character.sortOrder(), character.approved());
    }

    public void updateCharacter(Character character) {
        jdbcTemplate.update("""
            UPDATE character
            SET name = ?, role_description = ?, voice_key = ?, sort_order = ?, approved = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            character.name(), character.roleDescription(), character.voiceKey(),
            character.sortOrder(), character.approved(), character.id());
    }

    public void deleteCharacter(String characterId) {
        jdbcTemplate.update("DELETE FROM character WHERE id = ?", characterId);
    }

    // ==================== Speech Segment Management ====================

    public List<SpeechSegment> findSegmentsForProject(String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, character_id, sequence_no, original_text, annotated_text, edited, approved, created_at, updated_at
            FROM speech_segment
            WHERE project_id = ?
            ORDER BY sequence_no ASC
            """, segmentMapper(), projectId);
    }

    public Optional<SpeechSegment> findSegment(String segmentId, String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, character_id, sequence_no, original_text, annotated_text, edited, approved, created_at, updated_at
            FROM speech_segment
            WHERE id = ? AND project_id = ?
            """, segmentMapper(), segmentId, projectId).stream().findFirst();
    }

    public void createSegment(SpeechSegment segment) {
        jdbcTemplate.update("""
            INSERT INTO speech_segment (id, project_id, character_id, sequence_no, original_text, annotated_text, edited, approved, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            segment.id(), segment.projectId(), segment.characterId(), segment.sequenceNo(),
            segment.originalText(), segment.annotatedText(), segment.edited(), segment.approved());
    }

    public void updateSegment(SpeechSegment segment) {
        jdbcTemplate.update("""
            UPDATE speech_segment
            SET original_text = ?, annotated_text = ?, edited = ?, approved = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            segment.originalText(), segment.annotatedText(), segment.edited(), segment.approved(), segment.id());
    }

    public void deleteSegment(String segmentId) {
        jdbcTemplate.update("DELETE FROM speech_segment WHERE id = ?", segmentId);
    }

    // ==================== Generation & Render Tracking ====================

    public void createGenerationRun(AIGenerationRun run) {
        jdbcTemplate.update("""
            INSERT INTO ai_generation_run (id, project_id, type, status, request_json, response_json, error_message, started_at, completed_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            run.id(), run.projectId(), run.type().name(), run.status().name(),
            run.requestJson(), run.responseJson(), run.errorMessage(), run.startedAt(), run.completedAt());
    }

    public Optional<AIGenerationRun> findGenerationRun(String runId, String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, type, status, request_json, response_json, error_message, started_at, completed_at, created_at
            FROM ai_generation_run
            WHERE id = ? AND project_id = ?
            """, generationRunMapper(), runId, projectId).stream().findFirst();
    }

    public List<AIGenerationRun> findGenerationRunsForProject(String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, type, status, request_json, response_json, error_message, started_at, completed_at, created_at
            FROM ai_generation_run
            WHERE project_id = ?
            ORDER BY created_at DESC
            """, generationRunMapper(), projectId);
    }

    public void updateGenerationRunStatus(String runId, RunStatus status, String responseJson, String errorMessage) {
        jdbcTemplate.update("""
            UPDATE ai_generation_run
            SET status = ?, response_json = ?, error_message = ?, completed_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            status.name(), responseJson, errorMessage, runId);
    }

    public void createRenderSegment(RenderSegment render) {
        jdbcTemplate.update("""
            INSERT INTO render_segment (id, ai_generation_run_id, speech_segment_id, text, provider_request_json, status, audio_asset_id, created_at, started_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
            """,
            render.id(), render.aiGenerationRunId(), render.speechSegmentId(), render.text(),
            render.providerRequestJson(), render.status().name(), render.audioAssetId(), render.startedAt(), render.completedAt());
    }

    public Optional<RenderSegment> findRenderSegment(String renderId) {
        return jdbcTemplate.query("""
            SELECT id, ai_generation_run_id, speech_segment_id, text, provider_request_json, status, audio_asset_id, created_at, started_at, completed_at
            FROM render_segment
            WHERE id = ?
            """, renderSegmentMapper(), renderId).stream().findFirst();
    }

    public List<RenderSegment> findRenderSegmentsForRun(String runId) {
        return jdbcTemplate.query("""
            SELECT id, ai_generation_run_id, speech_segment_id, text, provider_request_json, status, audio_asset_id, created_at, started_at, completed_at
            FROM render_segment
            WHERE ai_generation_run_id = ?
            ORDER BY created_at ASC
            """, renderSegmentMapper(), runId);
    }

    public void updateRenderSegmentStatus(String renderId, RunStatus status, String audioAssetId) {
        jdbcTemplate.update("""
            UPDATE render_segment
            SET status = ?, audio_asset_id = ?, completed_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            status.name(), audioAssetId, renderId);
    }

    // ==================== Asset Management ====================

    public void createAudioAsset(AudioAsset asset) {
        jdbcTemplate.update("""
            INSERT INTO audio_asset (id, project_id, ai_generation_run_id, type, file_name, storage_key, content_type, duration_ms, size_bytes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            asset.id(), asset.projectId(), asset.aiGenerationRunId(), asset.type().name(),
            asset.fileName(), asset.storageKey(), asset.contentType(), asset.durationMs(), asset.sizeBytes());
    }

    public List<AudioAsset> findAssetsForProject(String projectId) {
        return jdbcTemplate.query("""
            SELECT id, project_id, ai_generation_run_id, type, file_name, storage_key, content_type, duration_ms, size_bytes, created_at
            FROM audio_asset
            WHERE project_id = ?
            ORDER BY created_at DESC
            """, assetMapper(), projectId);
    }

    public Optional<AudioAsset> findAssetForUser(String projectId, String assetId, String userId) {
        return jdbcTemplate.query("""
            SELECT aa.id, aa.project_id, aa.ai_generation_run_id, aa.type, aa.file_name, aa.storage_key, aa.content_type, aa.duration_ms, aa.size_bytes, aa.created_at
            FROM audio_asset aa
            INNER JOIN audiobook_project ap ON ap.id = aa.project_id
            WHERE aa.project_id = ? AND aa.id = ? AND ap.user_id = ?
            """, assetMapper(), projectId, assetId, userId).stream().findFirst();
    }

    public void deleteAsset(String assetId) {
        jdbcTemplate.update("DELETE FROM audio_asset WHERE id = ?", assetId);
    }

    // ==================== Transactional Operations ====================

    @Transactional
    public void createProjectWithCharactersAndSegments(AudiobookProject project, List<Character> characters, List<SpeechSegment> segments) {
        createProject(project);
        for (Character character : characters) {
            createCharacter(character);
        }
        for (SpeechSegment segment : segments) {
            createSegment(segment);
        }
    }

    @Transactional
    public void createGenerationRunWithRenders(AIGenerationRun run, List<RenderSegment> renders, List<AudioAsset> assets) {
        createGenerationRun(run);
        for (RenderSegment render : renders) {
            createRenderSegment(render);
        }
        for (AudioAsset asset : assets) {
            createAudioAsset(asset);
        }
    }

    // ==================== Row Mappers ====================

    private RowMapper<AudiobookProject> projectMapper() {
        return (rs, rowNum) -> new AudiobookProject(
            rs.getString("id"),
            rs.getString("user_id"),
            rs.getString("title"),
            rs.getString("source_text"),
            rs.getString("language_code"),
            rs.getString("model_name"),
            rs.getString("audio_encoding"),
            AudiobookProjectStatus.valueOf(rs.getString("status")),
            rs.getInt("revision"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private RowMapper<Character> characterMapper() {
        return (rs, rowNum) -> new Character(
            rs.getString("id"),
            rs.getString("project_id"),
            rs.getString("name"),
            rs.getString("role_description"),
            rs.getString("voice_key"),
            rs.getInt("sort_order"),
            rs.getBoolean("approved"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private RowMapper<SpeechSegment> segmentMapper() {
        return (rs, rowNum) -> new SpeechSegment(
            rs.getString("id"),
            rs.getString("project_id"),
            rs.getString("character_id"),
            rs.getInt("sequence_no"),
            rs.getString("original_text"),
            rs.getString("annotated_text"),
            rs.getBoolean("edited"),
            rs.getBoolean("approved"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private RowMapper<AIGenerationRun> generationRunMapper() {
        return (rs, rowNum) -> new AIGenerationRun(
            rs.getString("id"),
            rs.getString("project_id"),
            RunType.valueOf(rs.getString("type")),
            RunStatus.valueOf(rs.getString("status")),
            rs.getString("request_json"),
            rs.getString("response_json"),
            rs.getString("error_message"),
            instant(rs, "started_at"),
            instant(rs, "completed_at"),
            instant(rs, "created_at")
        );
    }

    private RowMapper<RenderSegment> renderSegmentMapper() {
        return (rs, rowNum) -> new RenderSegment(
            rs.getString("id"),
            rs.getString("ai_generation_run_id"),
            rs.getString("speech_segment_id"),
            rs.getString("text"),
            rs.getString("provider_request_json"),
            RunStatus.valueOf(rs.getString("status")),
            rs.getString("audio_asset_id"),
            instant(rs, "created_at"),
            instant(rs, "started_at"),
            instant(rs, "completed_at")
        );
    }

    private RowMapper<AudioAsset> assetMapper() {
        return (rs, rowNum) -> new AudioAsset(
            rs.getString("id"),
            rs.getString("project_id"),
            rs.getString("ai_generation_run_id"),
            AudioAssetType.valueOf(rs.getString("type")),
            rs.getString("file_name"),
            rs.getString("storage_key"),
            rs.getString("content_type"),
            longValue(rs, "duration_ms"),
            rs.getLong("size_bytes"),
            instant(rs, "created_at")
        );
    }

    // ==================== Utility Methods ====================

    private Instant instant(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Long longValue(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
