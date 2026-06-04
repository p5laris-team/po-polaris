CREATE TABLE mission_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMP NOT NULL,
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mission_outbox_events_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_mission_outbox_events_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED'
        )
    )
);

CREATE INDEX idx_mission_outbox_events_event_status_next_attempt
    ON mission_outbox_events(event_type, status, next_attempt_at);

INSERT INTO mission_outbox_events (
    aggregate_type,
    aggregate_id,
    event_type,
    payload,
    idempotency_key,
    status,
    attempt_count,
    next_attempt_at,
    last_error_message,
    created_at,
    updated_at
)
SELECT
    'MISSION',
    mission_id,
    'MISSION_REWARD_REQUESTED',
    jsonb_build_object(
        'missionId', mission_id,
        'userId', user_id,
        'rewardStarPiece', reward_star_piece
    ),
    idempotency_key,
    status,
    attempt_count,
    next_attempt_at,
    last_error_message,
    created_at,
    updated_at
FROM mission_reward_outbox
ON CONFLICT (idempotency_key) DO NOTHING;
