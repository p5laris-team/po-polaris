ALTER TABLE attendance_records ADD COLUMN streak_count INT NOT NULL DEFAULT 1;

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
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_spt_user_created ON star_piece_transactions(user_id, created_at);
CREATE INDEX idx_spt_reason_created ON star_piece_transactions(reason, created_at);
CREATE INDEX idx_spt_ref ON star_piece_transactions(ref_type, ref_id);
