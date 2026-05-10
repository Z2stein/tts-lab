package com.example.ttslab.audiobooks.workflow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository for WorkflowSession CRUD operations.
 * Uses JDBC for consistency with existing AudiobookRepository pattern.
 */
@Repository
public class WorkflowSessionRepository {

    private final JdbcTemplate jdbc;

    public WorkflowSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Create a new workflow session.
     */
    @Transactional
    public WorkflowSession createSession(WorkflowSession session) {
        String sql = """
            INSERT INTO workflow_session
            (id, project_id, user_id, state, story_text, created_at, updated_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbc.update(sql,
            session.id(),
            session.projectId(),
            session.userId(),
            session.state().name(),
            session.storyText(),
            session.createdAt(),
            session.updatedAt(),
            session.completedAt()
        );

        return session;
    }

    /**
     * Retrieve a session by ID with ownership check.
     */
    public Optional<WorkflowSession> findSessionForUser(String sessionId, String userId) {
        String sql = """
            SELECT id, project_id, user_id, state, story_text,
                   speaker_analysis_json, dialogue_split_json, annotation_json,
                   tts_config_json, render_plan_json, created_at, updated_at, completed_at
            FROM workflow_session
            WHERE id = ? AND user_id = ?
            """;

        return jdbc.query(sql, rs -> {
            if (rs.next()) {
                return Optional.of(new WorkflowSession(
                    rs.getString("id"),
                    rs.getString("project_id"),
                    rs.getString("user_id"),
                    WorkflowState.valueOf(rs.getString("state")),
                    rs.getString("story_text"),
                    rs.getString("speaker_analysis_json"),
                    rs.getString("dialogue_split_json"),
                    rs.getString("annotation_json"),
                    rs.getString("tts_config_json"),
                    rs.getString("render_plan_json"),
                    rs.getObject("created_at", Instant.class),
                    rs.getObject("updated_at", Instant.class),
                    rs.getObject("completed_at", Instant.class)
                ));
            }
            return Optional.empty();
        }, sessionId, userId);
    }

    /**
     * Update workflow state and persist new artifacts atomically.
     */
    @Transactional
    public WorkflowSession updateSession(WorkflowSession session) {
        String sql = """
            UPDATE workflow_session
            SET state = ?,
                story_text = ?, speaker_analysis_json = ?, dialogue_split_json = ?,
                annotation_json = ?, tts_config_json = ?, render_plan_json = ?,
                updated_at = ?, completed_at = ?
            WHERE id = ? AND user_id = ?
            """;

        jdbc.update(sql,
            session.state().name(),
            session.storyText(),
            session.speakerAnalysisJson(),
            session.dialogueSplitJson(),
            session.annotationJson(),
            session.ttsConfigJson(),
            session.renderPlanJson(),
            session.updatedAt(),
            session.completedAt(),
            session.id(),
            session.userId()
        );

        return session;
    }

    /**
     * Check if session exists and belongs to user.
     */
    public boolean sessionExists(String sessionId, String userId) {
        String sql = "SELECT 1 FROM workflow_session WHERE id = ? AND user_id = ? LIMIT 1";
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, sessionId, userId));
    }
}
