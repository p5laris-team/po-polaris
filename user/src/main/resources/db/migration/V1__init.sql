CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(255) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    refresh_token VARCHAR(512),
    weather_region_code VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_created_at ON users(created_at);

CREATE TABLE onboarding_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    living_type VARCHAR(50),
    wake_up_time VARCHAR(50),
    sleep_time VARCHAR(50),
    preferred_mission_time VARCHAR(50),
    routine_goal VARCHAR(50),
    activity_preference VARCHAR(50),
    mission_intensity VARCHAR(50),
    answers_json JSONB,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    onboarding_version INT NOT NULL DEFAULT 1,
    routine_goals_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_time_slots_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    mission_place_contexts_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    avoided_mission_tags_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_onboarding_profiles_completed ON onboarding_profiles(completed);

CREATE TABLE wallets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    star_piece INT NOT NULL DEFAULT 0 CHECK (star_piece >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE attendance_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    reward_star_piece INT NOT NULL DEFAULT 0,
    streak_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE(user_id, attendance_date)
);

CREATE INDEX idx_attendance_user_date ON attendance_records(user_id, attendance_date);

CREATE TABLE star_piece_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount INT NOT NULL,
    balance_after INT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    ref_type VARCHAR(50),
    ref_id BIGINT,
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_spt_user_created ON star_piece_transactions(user_id, created_at);
CREATE INDEX idx_spt_reason_created ON star_piece_transactions(reason, created_at);
CREATE INDEX idx_spt_ref ON star_piece_transactions(ref_type, ref_id);

CREATE OR REPLACE VIEW v_daily_wallet_economy AS
SELECT
    DATE(created_at) AS active_date,
    SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) AS total_issued,
    SUM(CASE WHEN amount < 0 THEN ABS(amount) ELSE 0 END) AS total_consumed,
    SUM(amount) AS net_increase
FROM star_piece_transactions
GROUP BY DATE(created_at);

CREATE TABLE user_outbox_events (
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
    CONSTRAINT uk_user_outbox_events_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_user_outbox_events_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'SUCCEEDED',
            'FAILED'
        )
    )
);

CREATE INDEX idx_user_outbox_events_status_next_attempt
    ON user_outbox_events(status, next_attempt_at);

CREATE TABLE payment_orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(100) UNIQUE NOT NULL,
    amount INT NOT NULL,
    star_pieces INT NOT NULL,
    status VARCHAR(20) NOT NULL, -- READY, PAID, CANCELLED, FAILED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_order_id BIGINT NOT NULL,
    payment_id VARCHAR(100) NOT NULL,
    pg_provider VARCHAR(50),
    pay_method VARCHAR(50),
    paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,
    cancel_amount INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_order_id) REFERENCES payment_orders(id) ON DELETE CASCADE
);

CREATE INDEX idx_payment_orders_user ON payment_orders(user_id);
CREATE INDEX idx_payment_transactions_order ON payment_transactions(payment_order_id);