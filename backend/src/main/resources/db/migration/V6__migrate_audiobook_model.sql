-- Drop existing audiobook schema
DROP TABLE IF EXISTS audio_asset CASCADE;
DROP TABLE IF EXISTS audiobook_scene CASCADE;
DROP TABLE IF EXISTS audiobook_project CASCADE;

-- New audiobook_project table with enhanced metadata
CREATE TABLE audiobook_project (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_text TEXT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    audio_encoding VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    revision INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audiobook_project_user ON audiobook_project (user_id);
CREATE INDEX idx_audiobook_project_updated ON audiobook_project (user_id, updated_at DESC);

-- New character table for cast management
CREATE TABLE character (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES audiobook_project(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    role_description TEXT,
    voice_key VARCHAR(255) NOT NULL,
    sort_order INTEGER NOT NULL,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, name)
);

CREATE INDEX idx_character_project ON character (project_id);
CREATE INDEX idx_character_sort ON character (project_id, sort_order);

-- Refactored speech_segment table (no embedded speaker data)
CREATE TABLE speech_segment (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES audiobook_project(id) ON DELETE CASCADE,
    character_id VARCHAR(36) NOT NULL REFERENCES character(id) ON DELETE RESTRICT,
    sequence_no INTEGER NOT NULL,
    original_text TEXT NOT NULL,
    annotated_text TEXT,
    edited BOOLEAN NOT NULL DEFAULT FALSE,
    approved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (project_id, sequence_no)
);

CREATE INDEX idx_speech_segment_project ON speech_segment (project_id);
CREATE INDEX idx_speech_segment_character ON speech_segment (character_id);
CREATE INDEX idx_speech_segment_sequence ON speech_segment (project_id, sequence_no);

-- New ai_generation_run table for tracking generation attempts
CREATE TABLE ai_generation_run (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES audiobook_project(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    request_json TEXT,
    response_json TEXT,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_generation_run_project ON ai_generation_run (project_id);
CREATE INDEX idx_ai_generation_run_status ON ai_generation_run (status);
CREATE INDEX idx_ai_generation_run_created ON ai_generation_run (project_id, created_at DESC);

-- New render_segment table for individual segment rendering
CREATE TABLE render_segment (
    id VARCHAR(36) PRIMARY KEY,
    ai_generation_run_id VARCHAR(36) NOT NULL REFERENCES ai_generation_run(id) ON DELETE CASCADE,
    speech_segment_id VARCHAR(36) NOT NULL REFERENCES speech_segment(id) ON DELETE RESTRICT,
    text TEXT NOT NULL,
    provider_request_json TEXT,
    status VARCHAR(64) NOT NULL,
    audio_asset_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_render_segment_run ON render_segment (ai_generation_run_id);
CREATE INDEX idx_render_segment_speech ON render_segment (speech_segment_id);
CREATE INDEX idx_render_segment_asset ON render_segment (audio_asset_id);
CREATE INDEX idx_render_segment_status ON render_segment (status);

-- Updated audio_asset table with generation run reference
CREATE TABLE audio_asset (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES audiobook_project(id) ON DELETE CASCADE,
    ai_generation_run_id VARCHAR(36) REFERENCES ai_generation_run(id) ON DELETE SET NULL,
    type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(128),
    duration_ms BIGINT,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audio_asset_project ON audio_asset (project_id, created_at DESC);
CREATE INDEX idx_audio_asset_run ON audio_asset (ai_generation_run_id);
