ALTER TABLE onboarding_profiles
    ADD COLUMN onboarding_version INT NOT NULL DEFAULT 1,
    ADD COLUMN routine_goals_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN preferred_time_slots_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN mission_place_contexts_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN avoided_mission_tags_json JSONB NOT NULL DEFAULT '[]'::jsonb;
