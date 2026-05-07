CREATE TABLE request_rate_limit_overrides (
    user_id VARCHAR(255) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    limit_amount BIGINT NOT NULL,
    unit VARCHAR(32) NOT NULL DEFAULT 'WORDS',
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, model_type)
);

CREATE TABLE request_usage_windows (
    user_id VARCHAR(255) NOT NULL,
    model_type VARCHAR(32) NOT NULL,
    window_bucket BIGINT NOT NULL,
    unit VARCHAR(32) NOT NULL DEFAULT 'WORDS',
    used_amount BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, model_type, window_bucket)
);

