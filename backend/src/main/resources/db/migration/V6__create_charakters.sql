CREATE TABLE charakters (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES audiobook_project(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    speaker_name VARCHAR(255) NOT NULL,
    role_description TEXT,
    voice_suggestion VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_charakters_project_order ON charakters (project_id, sort_order);
