CREATE UNIQUE INDEX uk_user_missions_user_date_active
    ON user_missions(user_id, mission_date)
    WHERE status IN ('OFFERED', 'ANSWERING');
