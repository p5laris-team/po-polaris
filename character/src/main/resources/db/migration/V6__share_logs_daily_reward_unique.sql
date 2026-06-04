-- 공유 보상은 유저/날짜당 1회만 지급 (reward_paid=true 인 경우만)
-- race condition 방지를 위해 DB 레벨에서 중복 지급을 차단한다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_share_logs_daily_reward_paid
    ON share_logs (user_id, share_date)
    WHERE reward_paid = TRUE;

