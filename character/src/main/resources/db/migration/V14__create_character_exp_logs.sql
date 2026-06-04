CREATE TABLE character_exp_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    exp_amount INT NOT NULL CHECK (exp_amount > 0),
    before_exp INT NOT NULL CHECK (before_exp >= 0),
    after_exp INT NOT NULL CHECK (after_exp >= 0),
    before_level INT NOT NULL CHECK (before_level >= 1),
    after_level INT NOT NULL CHECK (after_level >= 1),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_character_exp_logs_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT uk_character_exp_logs_source
        UNIQUE (source_type, source_id)
);

CREATE INDEX idx_character_exp_logs_character_created_at
    ON character_exp_logs(character_id, created_at);

CREATE INDEX idx_character_exp_logs_user_created_at
    ON character_exp_logs(user_id, created_at);
