CREATE TABLE character_outbox_events (
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
    CONSTRAINT uk_character_outbox_events_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_character_outbox_events_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED'
        )
    )
);

CREATE INDEX idx_character_outbox_events_status_next_attempt
    ON character_outbox_events(status, next_attempt_at);

CREATE INDEX idx_character_outbox_events_event_status_next_attempt
    ON character_outbox_events(event_type, status, next_attempt_at);

CREATE INDEX idx_character_outbox_events_aggregate
    ON character_outbox_events(aggregate_type, aggregate_id, event_type);
