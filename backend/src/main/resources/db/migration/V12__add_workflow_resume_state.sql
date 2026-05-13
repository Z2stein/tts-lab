ALTER TABLE audiobook_project ADD COLUMN story_text TEXT;
ALTER TABLE audiobook_project ADD COLUMN workflow_stage VARCHAR(32) DEFAULT 'CAST_REVIEW';
ALTER TABLE audiobook_project ADD COLUMN production_prompt TEXT;
ALTER TABLE audiobook_project ADD COLUMN production_language_code VARCHAR(32);
ALTER TABLE audiobook_project ADD COLUMN production_model_name VARCHAR(128);
ALTER TABLE audiobook_project ADD COLUMN production_audio_encoding VARCHAR(32);
