CREATE TABLE audiobook_project (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    scene_count INTEGER NOT NULL DEFAULT 0,
    speaker_count INTEGER,
    total_duration_seconds INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audiobook_project_user_updated ON audiobook_project (user_id, updated_at DESC);

CREATE TABLE audiobook_scene (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES audiobook_project(id) ON DELETE CASCADE,
    order_index INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    duration_seconds INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audiobook_scene_project_order ON audiobook_scene (project_id, order_index);

CREATE TABLE audio_asset (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES audiobook_project(id) ON DELETE CASCADE,
    scene_id VARCHAR(36) REFERENCES audiobook_scene(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    version INTEGER NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    duration_seconds INTEGER,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audio_asset_project ON audio_asset (project_id, created_at DESC);
CREATE INDEX idx_audio_asset_scene ON audio_asset (scene_id, created_at DESC);
