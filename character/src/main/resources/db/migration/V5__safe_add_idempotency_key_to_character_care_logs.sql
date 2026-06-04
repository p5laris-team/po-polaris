-- character_care_logs.idempotency_key migration (safe for existing rows)
-- 1) nullable 컬럼 추가
ALTER TABLE character_care_logs
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

-- 2) 기존 row backfill (중복 없이 채우기)
UPDATE character_care_logs
SET idempotency_key = 'CARE_LOG:' || id || ':' || EXTRACT(EPOCH FROM created_at)::bigint
WHERE idempotency_key IS NULL;

-- 3) unique index 추가
CREATE UNIQUE INDEX IF NOT EXISTS uq_character_care_logs_idempotency_key
    ON character_care_logs (idempotency_key);

-- 4) not null 제약 추가
ALTER TABLE character_care_logs
    ALTER COLUMN idempotency_key SET NOT NULL;

