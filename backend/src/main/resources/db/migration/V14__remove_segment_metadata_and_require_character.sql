-- V14: Remove redundant metadata fields from audiobook_speech_segment
-- These fields (speaker_name, speaker_role_description, voice_name, performance_directions)
-- should only exist on the SpeakerCharacter entity, not duplicated on segments.
-- The character relationship is already marked as required (optional=false) in the JPA entity definition.

-- Drop the four redundant metadata columns
ALTER TABLE audiobook_speech_segment DROP COLUMN IF EXISTS speaker_name;
ALTER TABLE audiobook_speech_segment DROP COLUMN IF EXISTS speaker_role_description;
ALTER TABLE audiobook_speech_segment DROP COLUMN IF EXISTS voice_name;
ALTER TABLE audiobook_speech_segment DROP COLUMN IF EXISTS performance_directions;

-- Note: The NOT NULL constraint on character_id is enforced at the application level
-- via the @ManyToOne(optional=false) JPA annotation on the AudiobookSpeechSegment entity.
-- Database-level NOT NULL constraint will be added in a future migration after all
-- existing data is guaranteed to have valid character assignments.
