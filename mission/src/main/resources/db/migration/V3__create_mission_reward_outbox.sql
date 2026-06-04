CREATE TABLE mission_reward_outbox (
    id BIGSERIAL PRIMARY KEY,
    mission_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reward_star_piece INT NOT NULL CHECK (reward_star_piece >= 0),
    idempotency_key VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMP NOT NULL,
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mission_reward_outbox_mission
        FOREIGN KEY (mission_id)
        REFERENCES user_missions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_mission_reward_outbox_mission
        UNIQUE (mission_id),
    CONSTRAINT uk_mission_reward_outbox_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_mission_reward_outbox_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED'
        )
    )
);

CREATE INDEX idx_mission_reward_outbox_status_next_attempt
    ON mission_reward_outbox(status, next_attempt_at);
