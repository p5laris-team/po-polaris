-- =============================================================
-- Polaris Character Domain - Initial Schema
-- ERD: 02_ERD_Data_Model.md §1.3 ~ §1.5
-- =============================================================

-- -------------------------------------------------------------
-- 1. character_types (ERD §1.3)
--    MVP 캐릭터 3종(NOVA, MUMU, JJORY)의 기본 정보
-- -------------------------------------------------------------
CREATE TABLE character_types (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    summary     VARCHAR(255) NOT NULL,
    personality TEXT         NOT NULL,
    speech_style TEXT        NOT NULL,
    intro_message TEXT       NOT NULL,
    sample_line VARCHAR(255) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_character_types_code UNIQUE (code)
);

CREATE INDEX idx_character_types_active_sort_order ON character_types (active, sort_order);

-- -------------------------------------------------------------
-- 2. character_assets (ERD §1.3)
--    캐릭터 타입별 이미지 에셋
-- -------------------------------------------------------------
CREATE TABLE character_assets (
    id                BIGSERIAL PRIMARY KEY,
    character_type_id BIGINT       NOT NULL REFERENCES character_types (id),
    asset_type        VARCHAR(50)  NOT NULL,
    asset_url         TEXT         NOT NULL
);

-- -------------------------------------------------------------
-- 3. user_characters (ERD §1.4)
--    사용자가 키우는 캐릭터 인스턴스
--
--    상태값 정책 (AGENTS.md §20.1):
--      fullness  : 포만감, 높을수록 좋음 (FEED 로 회복)
--      energy    : 에너지, 높을수록 좋음 (SLEEP 로 회복)
--      affection : 애정도, 높을수록 좋음 (PLAY 로 회복)
--    ※ ERD 초안 hunger_status → AGENTS.md 정책 우선으로 fullness 로 대체
-- -------------------------------------------------------------
CREATE TABLE user_characters (
    id                  BIGSERIAL    PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    character_type_id   BIGINT       NOT NULL REFERENCES character_types (id),
    name                VARCHAR(10)  NOT NULL,
    level               INT          NOT NULL DEFAULT 1,
    exp                 INT          NOT NULL DEFAULT 0,
    fullness            INT          NOT NULL DEFAULT 100 CHECK (fullness >= 0 AND fullness <= 100),
    energy              INT          NOT NULL DEFAULT 100 CHECK (energy >= 0 AND energy <= 100),
    affection           INT          NOT NULL DEFAULT 100 CHECK (affection >= 0 AND affection <= 100),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    equipped_skin_id    BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_characters_user_id_active       ON user_characters (user_id, active);
CREATE INDEX idx_user_characters_character_type_id    ON user_characters (character_type_id);
-- 사용자당 활성 캐릭터는 1개만 허용
CREATE UNIQUE INDEX uq_user_characters_user_active
    ON user_characters (user_id) WHERE active = TRUE;

-- -------------------------------------------------------------
-- 4. character_care_logs (ERD §1.5)
--    돌봄 액션(FEED / SLEEP / PLAY) 기록
-- -------------------------------------------------------------
CREATE TABLE character_care_logs (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    character_id      BIGINT       NOT NULL REFERENCES user_characters (id),
    item_id           BIGINT,                          -- 소모성 아이템 사용 시 (nullable)
    action_type       VARCHAR(20)  NOT NULL,           -- FEED / SLEEP / PLAY
    before_state_json TEXT,
    after_state_json  TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_care_logs_user_id_created_at       ON character_care_logs (user_id, created_at);
CREATE INDEX idx_care_logs_character_id_created_at  ON character_care_logs (character_id, created_at);

-- -------------------------------------------------------------
-- 5. share_cards (ERD §1.5)
--    캐릭터 카드 공유용 공유 카드 정보
-- -------------------------------------------------------------
CREATE TABLE share_cards (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    character_id BIGINT       NOT NULL REFERENCES user_characters (id),
    image_url    TEXT,
    share_url    VARCHAR(512) NOT NULL
);

-- -------------------------------------------------------------
-- 6. share_logs (ERD §1.5)
--    SNS 공유 이벤트 + 보상 로그
--    정책: 공유 보상은 하루 1회 (AGENTS.md §20.5)
--    idempotency_key 형식: SHARE_REWARD:{userId}:{shareDate}
-- -------------------------------------------------------------
CREATE TABLE share_logs (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    character_id      BIGINT       NOT NULL REFERENCES user_characters (id),
    share_card_id     BIGINT       NOT NULL REFERENCES share_cards (id),
    share_type        VARCHAR(50)  NOT NULL,
    platform          VARCHAR(50)  NOT NULL,
    shared_at         TIMESTAMP    NOT NULL,
    share_date        DATE         NOT NULL,
    reward_star_piece INT          NOT NULL DEFAULT 0,
    reward_paid       BOOLEAN      NOT NULL DEFAULT FALSE,
    idempotency_key   VARCHAR(255) NOT NULL,

    CONSTRAINT uq_share_logs_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_share_logs_user_id_share_date ON share_logs (user_id, share_date);