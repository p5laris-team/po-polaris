CREATE OR REPLACE VIEW v_daily_user_activity AS
SELECT
    DATE(occurred_at) AS active_date,
    COUNT(DISTINCT user_id) AS dau,
    COUNT(DISTINCT CASE WHEN event_type = 'USER_SIGNED_UP' THEN user_id END) AS new_users,
    COUNT(DISTINCT CASE WHEN event_type IN ('MISSION_COMPLETED', 'MISSION_OFFERED', 'SHARE_COMPLETED') THEN user_id END) AS core_action_users
FROM event_logs
GROUP BY DATE(occurred_at);

CREATE OR REPLACE VIEW v_daily_mission_funnel AS
SELECT
    DATE(occurred_at) AS active_date,
    COUNT(CASE WHEN event_type = 'ONBOARDING_COMPLETED' THEN 1 END) AS onboarding_completed_events,
    COUNT(CASE WHEN event_type = 'CHARACTER_CREATED' THEN 1 END) AS character_created_events,
    COUNT(CASE WHEN event_type = 'MISSION_OFFERED' THEN 1 END) AS mission_offered_events,
    COUNT(CASE WHEN event_type = 'MISSION_REJECTED' THEN 1 END) AS mission_rejected_events,
    COUNT(CASE WHEN event_type = 'MISSION_COMPLETED' THEN 1 END) AS mission_completed_events
FROM event_logs
GROUP BY DATE(occurred_at);
