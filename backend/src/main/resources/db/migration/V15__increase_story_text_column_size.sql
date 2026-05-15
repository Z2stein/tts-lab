-- Increase story_text column size to support longer narrative text
ALTER TABLE audiobook_project ALTER COLUMN story_text SET DATA TYPE TEXT;
