CREATE OR REPLACE VIEW v_daily_share_store_activity AS
SELECT
    DATE(occurred_at) AS active_date,
    COUNT(CASE WHEN event_type = 'SHARE_CARD_CREATED' THEN 1 END) AS share_card_created_events,
    COUNT(CASE WHEN event_type = 'SHARE_COMPLETED' THEN 1 END) AS share_completed_events,
    COUNT(CASE WHEN event_type = 'SHARE_REWARD_CLAIMED' THEN 1 END) AS share_reward_claimed_events,
    COUNT(CASE WHEN event_type = 'ITEM_PURCHASED' THEN 1 END) AS item_purchased_events,
    COUNT(DISTINCT CASE WHEN event_type = 'ITEM_PURCHASED' THEN user_id END) AS unique_buyers
FROM event_logs
WHERE event_type IN ('SHARE_CARD_CREATED', 'SHARE_COMPLETED', 'SHARE_REWARD_CLAIMED', 'ITEM_PURCHASED')
GROUP BY DATE(occurred_at);
