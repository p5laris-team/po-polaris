-- 아이템 구매 이력 테이블
-- gRPC 클라이언트(gateway 등)가 전달한 멱등키로 중복 결제 및 중복 지급을 방지한다.
CREATE TABLE item_purchase_histories (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    user_item_id    BIGINT NOT NULL REFERENCES user_items(id),
    item_id         BIGINT NOT NULL REFERENCES items(id),
    quantity        INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    price           INT NOT NULL,
    star_piece      INT NOT NULL,
    transaction_id  BIGINT NOT NULL,
    idempotency_key VARCHAR(100) UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_item_purchase_histories_user_id ON item_purchase_histories(user_id);
CREATE INDEX idx_item_purchase_histories_user_item_id ON item_purchase_histories(user_item_id);
