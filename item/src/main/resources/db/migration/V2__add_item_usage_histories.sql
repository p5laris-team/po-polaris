-- 아이템 사용 이력 테이블
-- character 모듈에서 돌봄 액션 시 소모성(CONSUMABLE) 아이템 사용 이력을 기록한다.
CREATE TABLE item_usage_histories (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    user_item_id    BIGINT NOT NULL REFERENCES user_items(id),
    item_id         BIGINT NOT NULL REFERENCES items(id),
    quantity        INT NOT NULL DEFAULT 1 CHECK (quantity > 0),

    -- 어떤 컨텍스트에서 사용되었는지 (예: CARE_ACTION)
    ref_type        VARCHAR(50),
    -- 해당 컨텍스트의 PK (예: care_log_id)
    ref_id          BIGINT,

    -- 멱등키: 동일 요청 중복 처리 방지
    idempotency_key VARCHAR(100) UNIQUE,

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_item_usage_histories_user_id ON item_usage_histories(user_id);
CREATE INDEX idx_item_usage_histories_user_item_id ON item_usage_histories(user_item_id);
