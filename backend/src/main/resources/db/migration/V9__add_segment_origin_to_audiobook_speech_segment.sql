ALTER TABLE audiobook_speech_segment ADD COLUMN segment_origin VARCHAR(32) NOT NULL DEFAULT 'LEGACY';

UPDATE audiobook_speech_segment
SET segment_origin = 'SCRIPT_PREVIEW'
WHERE title LIKE 'Speech segment %' AND speaker_name IS NULL;
