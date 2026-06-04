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
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_onboarding_profiles_completed ON onboarding_profiles(completed);
