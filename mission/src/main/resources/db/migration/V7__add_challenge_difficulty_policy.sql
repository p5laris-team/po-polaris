ALTER TABLE mission_templates
    DROP CONSTRAINT chk_mission_templates_difficulty;

ALTER TABLE mission_templates
    ADD CONSTRAINT chk_mission_templates_difficulty CHECK (
        difficulty IN ('EASY', 'NORMAL', 'CHALLENGE')
    );

ALTER TABLE user_missions
    DROP CONSTRAINT chk_user_missions_difficulty;

ALTER TABLE user_missions
    ADD CONSTRAINT chk_user_missions_difficulty CHECK (
        difficulty IN ('EASY', 'NORMAL', 'CHALLENGE')
    );

UPDATE mission_templates
SET reward_star_piece = 15
WHERE difficulty = 'NORMAL'
  AND reward_star_piece < 15;
