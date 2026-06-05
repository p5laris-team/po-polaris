-- vector 익스텐션 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- prompt_templates 테이블 생성
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

-- ai_mission_generations 테이블 생성
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
    request_id VARCHAR(120) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
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
            'RATE_LIMIT_UNAVAILABLE',
            'INVALID_OUTPUT',
            'POLICY_VIOLATION',
            'PROVIDER_ERROR',
            'UNKNOWN'
        )
    ),
    CONSTRAINT uk_ai_mission_generations_request_id
        UNIQUE (request_id),
    CONSTRAINT chk_ai_mission_generations_request_hash
        CHECK (LENGTH(request_hash) = 64)
);

CREATE INDEX idx_ai_mission_generations_user_created_at
    ON ai_mission_generations(user_id, created_at);

CREATE INDEX idx_ai_mission_generations_status_created_at
    ON ai_mission_generations(status, created_at);

CREATE INDEX idx_ai_mission_generations_model_created_at
    ON ai_mission_generations(model, created_at);

-- ai_usage_logs 테이블 생성
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
            'RATE_LIMIT_UNAVAILABLE',
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

-- character_talk_sessions 테이블 생성
CREATE TABLE character_talk_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(80) NOT NULL,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    character_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    last_message_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    message_count INT NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    total_actual_prompt_tokens INT CHECK (total_actual_prompt_tokens >= 0),
    total_actual_completion_tokens INT CHECK (total_actual_completion_tokens >= 0),
    total_actual_tokens INT CHECK (total_actual_tokens >= 0),
    summary_created_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_character_talk_sessions_session_id UNIQUE (session_id),
    CONSTRAINT chk_character_talk_sessions_status CHECK (
        status IN ('ACTIVE', 'EXPIRED', 'MEMORY_READY')
    )
);

CREATE INDEX idx_character_talk_sessions_user_character_expires
    ON character_talk_sessions(user_id, character_id, expires_at DESC);

CREATE INDEX idx_character_talk_sessions_status_expires
    ON character_talk_sessions(status, expires_at, id);

-- character_talk_messages 테이블 생성
CREATE TABLE character_talk_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sequence INT NOT NULL CHECK (sequence > 0),
    request_id VARCHAR(120) NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_character_talk_messages_session
        FOREIGN KEY (session_id)
        REFERENCES character_talk_sessions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_character_talk_messages_session_sequence
        UNIQUE (session_id, sequence),
    CONSTRAINT chk_character_talk_messages_role CHECK (
        role IN ('USER', 'ASSISTANT')
    )
);

CREATE INDEX idx_character_talk_messages_session_sequence
    ON character_talk_messages(session_id, sequence);

CREATE INDEX idx_character_talk_messages_created_at
    ON character_talk_messages(created_at);

-- character_talk_memories 테이블 생성
CREATE TABLE character_talk_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    source_session_id BIGINT NOT NULL,
    memory_type VARCHAR(30) NOT NULL,
    summary TEXT NOT NULL,
    embedding_model VARCHAR(80) NOT NULL,
    embedding_dimension INT NOT NULL,
    embedding vector(768),
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_character_talk_memories_session
        FOREIGN KEY (source_session_id)
        REFERENCES character_talk_sessions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_character_talk_memories_session_type
        UNIQUE (source_session_id, memory_type),
    CONSTRAINT chk_character_talk_memories_type CHECK (
        memory_type IN ('SESSION_SUMMARY')
    ),
    CONSTRAINT chk_character_talk_memories_dimension CHECK (
        embedding_dimension = 768
    )
);

CREATE INDEX idx_character_talk_memories_user_character_created
    ON character_talk_memories(user_id, character_id, created_at DESC);

-- v_daily_ai_quality 뷰 생성
CREATE OR REPLACE VIEW v_daily_ai_quality AS
SELECT
    DATE(created_at) AS active_date,
    COUNT(*) AS total_ai_requests,
    COUNT(CASE WHEN fallback_used = true THEN 1 END) AS fallback_requests,
    COUNT(CASE WHEN error_type = 'RATE_LIMIT_UNAVAILABLE' THEN 1 END) AS rate_limit_errors,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_requests
FROM ai_mission_generations
GROUP BY DATE(created_at);

-- daily_character_talk_metrics_view 뷰 생성
CREATE OR REPLACE VIEW daily_character_talk_metrics_view AS
SELECT
    DATE(created_at) AS metric_date,
    COUNT(*) AS session_count,
    SUM(message_count) AS message_count,
    AVG(total_actual_tokens) AS avg_actual_tokens,
    MAX(total_actual_tokens) AS max_actual_tokens,
    AVG(total_actual_prompt_tokens) AS avg_actual_prompt_tokens,
    AVG(total_actual_completion_tokens) AS avg_actual_completion_tokens
FROM character_talk_sessions
GROUP BY DATE(created_at);

-- 시드 데이터 삽입
INSERT INTO prompt_templates (
    name,
    category,
    template,
    version,
    active
) VALUES (
    'mission_text_character_tone',
    'CHARACTER_TONE',
    '선택된 seed 미션의 제목, 설명, 보상, 카테고리는 변경하지 않는다. 캐릭터 말투로 characterMessage, completionQuestion, completionCharacterResponse만 생성한다. 각 문구는 짧고 죄책감이나 비난을 유발하지 않아야 한다.',
    1,
    FALSE
);

INSERT INTO prompt_templates (
    name,
    category,
    template,
    version,
    active
) VALUES (
    'autonomous-mission-generation',
    'MISSION_GENERATION',
    '온보딩 context, 최근 미션 이력, 완료 답변, 피드백을 바탕으로 자율 미션 후보를 JSON으로 생성한다. title과 description은 사용자가 바로 읽는 일반 한국어 문장으로 작성하고, 캐릭터 발화나 "(해석: ...)" 형식을 절대 넣지 않는다. 캐릭터 말투는 characterMessage, completionQuestion, completionCharacterResponse에만 적용한다. allowedDifficulties 안에서 missionIntensity를 목표 난이도로 우선 반영해 EASY/NORMAL/CHALLENGE를 고르고, CHALLENGE는 하루 1회 정책과 안전한 동작 범위 안에서만 사용한다. 운동 NORMAL은 저강도라도 3~5분 반복 미션으로 만들 수 있다. 보상은 AI가 결정하지 않고 mission 서버 정책이 확정한다. 금지 표현, 길이, enum, CHALLENGE 하루 1회 제한은 서버 검증을 통과해야 한다.',
    1,
    TRUE
)
ON CONFLICT (name, version) DO UPDATE
SET category = EXCLUDED.category,
    template = EXCLUDED.template,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;
