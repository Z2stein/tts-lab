CREATE VIEW prompt_history_users AS
SELECT DISTINCT user_id, user_email
FROM prompt_history;

CREATE VIEW audiobook_segment_audio AS
SELECT p.title, s.order_index, s.original_text, a.storage_key
FROM audiobook_speech_segment AS s
    LEFT JOIN audio_asset AS a
        ON s.id = a.speech_segment_id
    LEFT JOIN audiobook_project AS p
        ON s.project_id = p.id;
