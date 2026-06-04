CREATE TABLE user_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    memory_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    importance INT NOT NULL DEFAULT 50,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_memories_source_type_id_memory_type
        UNIQUE (source_type, source_id, memory_type),
    CONSTRAINT chk_user_memories_source_type CHECK (
        source_type IN (
            'MISSION_COMPLETION_ANSWER',
            'MISSION_FEEDBACK'
        )
    ),
    CONSTRAINT chk_user_memories_memory_type CHECK (
        memory_type IN (
            'MISSION_COMPLETION',
            'MISSION_REJECTION',
            'MISSION_SATISFACTION'
        )
    ),
    CONSTRAINT chk_user_memories_importance CHECK (
        importance BETWEEN 0 AND 100
    )
);

CREATE INDEX idx_user_memories_user_created_at
    ON user_memories(user_id, created_at DESC);

CREATE INDEX idx_user_memories_user_type_created_at
    ON user_memories(user_id, memory_type, created_at DESC);
