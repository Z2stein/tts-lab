CREATE TABLE prompt_usage (
    user_id VARCHAR(255) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, model_type)
);
