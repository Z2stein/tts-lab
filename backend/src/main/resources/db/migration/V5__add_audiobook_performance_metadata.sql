-- Add performance metadata columns to audiobook_scene table
ALTER TABLE audiobook_scene ADD COLUMN speaker_name VARCHAR(255);
ALTER TABLE audiobook_scene ADD COLUMN speaker_role_description TEXT;
ALTER TABLE audiobook_scene ADD COLUMN voice_name VARCHAR(255);
ALTER TABLE audiobook_scene ADD COLUMN performance_directions TEXT;
