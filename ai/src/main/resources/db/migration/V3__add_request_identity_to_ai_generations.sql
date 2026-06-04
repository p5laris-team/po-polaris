ALTER TABLE ai_mission_generations
    ADD COLUMN request_id VARCHAR(120),
    ADD COLUMN request_hash VARCHAR(64);

UPDATE ai_mission_generations
SET request_id = CONCAT('MIGRATED:', id)
WHERE request_id IS NULL;

UPDATE ai_mission_generations
SET request_hash = REPEAT('0', 64)
WHERE request_hash IS NULL;

ALTER TABLE ai_mission_generations
    ALTER COLUMN request_id SET NOT NULL,
    ALTER COLUMN request_hash SET NOT NULL;

ALTER TABLE ai_mission_generations
    ADD CONSTRAINT uk_ai_mission_generations_request_id
        UNIQUE (request_id),
    ADD CONSTRAINT chk_ai_mission_generations_request_hash
        CHECK (LENGTH(request_hash) = 64);
