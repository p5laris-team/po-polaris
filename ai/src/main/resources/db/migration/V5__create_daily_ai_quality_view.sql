CREATE OR REPLACE VIEW v_daily_ai_quality AS
SELECT
    DATE(created_at) AS active_date,
    COUNT(*) AS total_ai_requests,
    COUNT(CASE WHEN fallback_used = true THEN 1 END) AS fallback_requests,
    COUNT(CASE WHEN error_type = 'RATE_LIMIT_UNAVAILABLE' THEN 1 END) AS rate_limit_errors,
    COUNT(CASE WHEN status = 'FAILED' THEN 1 END) AS failed_requests
FROM ai_mission_generations
GROUP BY DATE(created_at);
