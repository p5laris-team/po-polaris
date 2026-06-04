CREATE OR REPLACE VIEW v_daily_wallet_economy AS
SELECT
    DATE(created_at) AS active_date,
    SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END) AS total_issued,
    SUM(CASE WHEN amount < 0 THEN ABS(amount) ELSE 0 END) AS total_consumed,
    SUM(amount) AS net_increase
FROM star_piece_transactions
GROUP BY DATE(created_at);
