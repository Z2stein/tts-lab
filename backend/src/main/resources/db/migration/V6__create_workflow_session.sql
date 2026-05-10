-- V7: Create workflow_session table for audiobook generation workflow tracking
-- This table stores the state machine for multi-step audiobook creation workflows

CREATE TABLE IF NOT EXISTS workflow_session (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    state VARCHAR(64) NOT NULL,

    -- Intermediate artifact storage (JSON)
    story_text TEXT,
    speaker_analysis_json TEXT,
    dialogue_split_json TEXT,
    annotation_json TEXT,
    tts_config_json TEXT,
    render_plan_json TEXT,

    -- Audit trail
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_user ON workflow_session(user_id);
CREATE INDEX IF NOT EXISTS idx_workflow_state ON workflow_session(state);
CREATE INDEX IF NOT EXISTS idx_workflow_project ON workflow_session(project_id);
