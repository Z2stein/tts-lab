ALTER TABLE audiobook_scene RENAME TO audiobook_speech_segment;
ALTER TABLE audiobook_project RENAME COLUMN scene_count TO speech_segment_count;
ALTER TABLE audio_asset RENAME COLUMN scene_id TO speech_segment_id;
ALTER INDEX idx_audiobook_scene_project_order RENAME TO idx_audiobook_speech_segment_project_order;
ALTER INDEX idx_audio_asset_scene RENAME TO idx_audio_asset_speech_segment;
UPDATE audio_asset SET type = 'SPEECH_SEGMENT_MP3' WHERE type = 'SCENE_MP3';
