CREATE TABLE mission_feedbacks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mission_id BIGINT NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    reaction VARCHAR(30),
    reason_code VARCHAR(50),
    reason_text VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mission_feedbacks_mission
        FOREIGN KEY (mission_id)
        REFERENCES user_missions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_mission_feedbacks_user_mission_type
        UNIQUE (user_id, mission_id, feedback_type),
    CONSTRAINT chk_mission_feedbacks_type CHECK (
        feedback_type IN ('REJECTION', 'SATISFACTION')
    ),
    CONSTRAINT chk_mission_feedbacks_reaction CHECK (
        reaction IS NULL OR reaction IN ('LIKE', 'DISLIKE')
    ),
    CONSTRAINT chk_mission_feedbacks_reason_code CHECK (
        reason_code IS NULL OR reason_code IN (
            'NOT_NOW',
            'TOO_HARD',
            'NOT_INTERESTED',
            'ALREADY_DONE',
            'LOCATION_MISMATCH',
            'MOOD_MISMATCH',
            'REPEAT',
            'JUST_SKIP',
            'OTHER'
        )
    )
);

CREATE INDEX idx_mission_feedbacks_user_created_at
    ON mission_feedbacks(user_id, created_at DESC);
