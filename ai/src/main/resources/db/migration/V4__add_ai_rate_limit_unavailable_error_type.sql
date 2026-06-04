ALTER TABLE ai_mission_generations
    DROP CONSTRAINT chk_ai_mission_generations_error_type;

ALTER TABLE ai_mission_generations
    ADD CONSTRAINT chk_ai_mission_generations_error_type CHECK (
        error_type IS NULL OR error_type IN (
            'TIMEOUT',
            'RATE_LIMIT',
            'RATE_LIMIT_UNAVAILABLE',
            'INVALID_OUTPUT',
            'POLICY_VIOLATION',
            'PROVIDER_ERROR',
            'UNKNOWN'
        )
    );

ALTER TABLE ai_usage_logs
    DROP CONSTRAINT chk_ai_usage_logs_error_type;

ALTER TABLE ai_usage_logs
    ADD CONSTRAINT chk_ai_usage_logs_error_type CHECK (
        error_type IS NULL OR error_type IN (
            'TIMEOUT',
            'RATE_LIMIT',
            'RATE_LIMIT_UNAVAILABLE',
            'INVALID_OUTPUT',
            'POLICY_VIOLATION',
            'PROVIDER_ERROR',
            'UNKNOWN'
        )
    );
