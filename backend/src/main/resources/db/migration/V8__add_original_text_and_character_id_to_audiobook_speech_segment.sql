ALTER TABLE audiobook_speech_segment ADD COLUMN original_text TEXT;
ALTER TABLE audiobook_speech_segment ADD COLUMN character_id VARCHAR(36);

ALTER TABLE audiobook_speech_segment ADD CONSTRAINT fk_audiobook_speech_segment_character
    FOREIGN KEY (character_id) REFERENCES charakters(id);
