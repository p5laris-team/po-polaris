CREATE TABLE prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    template TEXT NOT NULL,
    version INT NOT NULL CHECK (version > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_prompt_templates_name_version
        UNIQUE (name, version),
    CONSTRAINT chk_prompt_templates_category CHECK (
        category IN (
            'MISSION_GENERATION',
            'CHARACTER_TONE',
            'COMPLETION_QA',
            'FALLBACK'
        )
    )
);

CREATE INDEX idx_prompt_templates_category_active
    ON prompt_templates(category, active);

CREATE TABLE ai_mission_generations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    prompt_template_id BIGINT,
    request_context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_json JSONB,
    selected_template_id BIGINT,
    status VARCHAR(30) NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    model VARCHAR(100),
    error_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_mission_generations_prompt_template
        FOREIGN KEY (prompt_template_id)
        REFERENCES prompt_templates(id)
        ON DELETE SET NULL,
    CONSTRAINT chk_ai_mission_generations_status CHECK (
        status IN ('SUCCESS', 'FALLBACK', 'FAILED')
    ),
    CONSTRAINT chk_ai_mission_generations_error_type CHECK (
        error_type IS NULL OR error_type IN (
            'TIMEOUT',
            'RATE_LIMIT',
            'INVALID_OUTPUT',
            'POLICY_VIOLATION',
            'PROVIDER_ERROR',
            'UNKNOWN'
        )
    )
);

CREATE INDEX idx_ai_mission_generations_user_created_at
    ON ai_mission_generations(user_id, created_at);

CREATE INDEX idx_ai_mission_generations_status_created_at
    ON ai_mission_generations(status, created_at);

CREATE INDEX idx_ai_mission_generations_model_created_at
    ON ai_mission_generations(model, created_at);

CREATE TABLE ai_usage_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    request_id VARCHAR(120) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0 CHECK (prompt_tokens >= 0),
    completion_tokens INT NOT NULL DEFAULT 0 CHECK (completion_tokens >= 0),
    total_tokens INT NOT NULL DEFAULT 0 CHECK (total_tokens >= 0),
    latency_ms INT NOT NULL DEFAULT 0 CHECK (latency_ms >= 0),
    status VARCHAR(30) NOT NULL,
    error_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_usage_logs_request_id
        UNIQUE (request_id),
    CONSTRAINT chk_ai_usage_logs_status CHECK (
        status IN ('SUCCESS', 'FAILED', 'FALLBACK', 'RATE_LIMITED')
    ),
    CONSTRAINT chk_ai_usage_logs_error_type CHECK (
        error_type IS NULL OR error_type IN (
            'TIMEOUT',
            'RATE_LIMIT',
            'INVALID_OUTPUT',
            'POLICY_VIOLATION',
            'PROVIDER_ERROR',
            'UNKNOWN'
        )
    )
);

CREATE INDEX idx_ai_usage_logs_user_created_at
    ON ai_usage_logs(user_id, created_at);

CREATE INDEX idx_ai_usage_logs_model_created_at
    ON ai_usage_logs(model, created_at);

CREATE INDEX idx_ai_usage_logs_status_created_at
    ON ai_usage_logs(status, created_at);
