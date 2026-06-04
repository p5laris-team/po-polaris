ALTER TABLE item_purchase_histories
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
ADD COLUMN next_attempt_at TIMESTAMP;

-- 이미 존재하는 과거 데이터들은 COMPLETED 로 설정
UPDATE item_purchase_histories SET status = 'COMPLETED';

CREATE INDEX idx_item_purchase_histories_status_next_attempt
    ON item_purchase_histories(status, next_attempt_at);
