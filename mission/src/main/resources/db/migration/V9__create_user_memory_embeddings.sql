CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE user_memory_embeddings (
    id BIGSERIAL PRIMARY KEY,
    user_memory_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    embedding_model VARCHAR(80) NOT NULL,
    embedding_dimension INT NOT NULL,
    embedding vector(768),
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error_message TEXT,
    embedded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_memory_embeddings_user_memory
        FOREIGN KEY (user_memory_id) REFERENCES user_memories(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_memory_embeddings_memory_model_dimension
        UNIQUE (user_memory_id, embedding_model, embedding_dimension),
    CONSTRAINT chk_user_memory_embeddings_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT chk_user_memory_embeddings_dimension CHECK (
        embedding_dimension = 768
    ),
    CONSTRAINT chk_user_memory_embeddings_attempt_count CHECK (
        attempt_count >= 0
    )
);

CREATE INDEX idx_user_memory_embeddings_status_next_attempt
    ON user_memory_embeddings(status, next_attempt_at, id);

CREATE INDEX idx_user_memory_embeddings_user_status
    ON user_memory_embeddings(user_id, status);

INSERT INTO user_memory_embeddings (
    user_memory_id,
    user_id,
    embedding_model,
    embedding_dimension,
    status,
    next_attempt_at
)
SELECT
    id,
    user_id,
    'gemini-embedding-001',
    768,
    'PENDING',
    CURRENT_TIMESTAMP
FROM user_memories
ON CONFLICT (user_memory_id, embedding_model, embedding_dimension) DO NOTHING;
