-- =========================================================================
-- Polaris 부하/장애 시뮬레이션 결과 검증 및 전체 마이크로서비스 클린업 SQL
-- 
-- [안내] 마이크로서비스별로 물리 DB(또는 스키마)가 분리되어 있으므로,
-- 각 데이터베이스 세션(커넥션)에 접속한 뒤 해당하는 섹션의 쿼리만 블록 지정하여 실행하십시오.
-- =========================================================================

-- =========================================================================
-- [1] USER (회원 / 재화 / 지갑) 데이터베이스 세션에서 실행
-- =========================================================================

-- 1-1) 멱등키 위반 및 보상 중복 수령 검사
-- 결과 행이 0건이어야 멱등성이 보장된 것입니다.
SELECT idempotency_key, COUNT(*) 
FROM star_piece_transactions 
WHERE idempotency_key IS NOT NULL 
GROUP BY idempotency_key 
HAVING COUNT(*) > 1;

-- 1-2) 거래원장 합계액과 실제 지갑 잔액 불일치 검사
-- 결과 행이 0건이어야 잔액 정합성이 지켜진 것입니다.
SELECT 
    w.user_id, 
    w.star_piece AS wallet_balance, 
    (500 + COALESCE(SUM(CASE WHEN t.transaction_type = 'EARN' THEN t.amount WHEN t.transaction_type = 'SPEND' THEN -t.amount ELSE 0 END), 0)) AS calculated_balance
FROM wallets w 
LEFT JOIN star_piece_transactions t ON w.user_id = t.user_id 
WHERE w.user_id >= 900001
GROUP BY w.user_id, w.star_piece
HAVING w.star_piece <> (500 + COALESCE(SUM(CASE WHEN t.transaction_type = 'EARN' THEN t.amount WHEN t.transaction_type = 'SPEND' THEN -t.amount ELSE 0 END), 0));

-- 1-3) 가상 테스트 데이터 삭제 (롤백)
BEGIN;
DELETE FROM star_piece_transactions WHERE user_id >= 900001;
DELETE FROM wallets WHERE user_id >= 900001;
DELETE FROM onboarding_profiles WHERE user_id >= 900001;
DELETE FROM attendance_records WHERE user_id >= 900001;
DELETE FROM payment_transactions WHERE user_id >= 900001;
DELETE FROM payment_orders WHERE user_id >= 900001;
DELETE FROM outbox_events WHERE aggregate_id >= 900001;
DELETE FROM users WHERE id >= 900001 OR email LIKE 'simulation_test_%';
COMMIT;


-- =========================================================================
-- [2] MISSION (미션 / RAG) 데이터베이스 세션에서 실행
-- =========================================================================
BEGIN;
DELETE FROM user_memories WHERE user_id >= 900001;
DELETE FROM mission_feedbacks WHERE user_id >= 900001;
DELETE FROM mission_completion_answers WHERE user_id >= 900001;
DELETE FROM user_missions WHERE user_id >= 900001;
DELETE FROM mission_outbox_events WHERE aggregate_id >= 900001;
COMMIT;


-- =========================================================================
-- [3] CHARACTER / SHARE (캐릭터 및 SNS 공유) 데이터베이스 세션에서 실행
-- =========================================================================
BEGIN;
DELETE FROM share_logs WHERE user_id >= 900001;
DELETE FROM share_cards WHERE user_id >= 900001;
DELETE FROM character_exp_logs WHERE user_id >= 900001;
DELETE FROM character_care_logs WHERE user_id >= 900001;
DELETE FROM user_characters WHERE user_id >= 900001;
DELETE FROM character_outbox_events WHERE aggregate_id >= 900001;
COMMIT;


-- =========================================================================
-- [4] ITEM (아이템 / 상점) 데이터베이스 세션에서 실행
-- =========================================================================
BEGIN;
DELETE FROM item_usage_histories WHERE user_id >= 900001;
DELETE FROM item_purchase_histories WHERE user_id >= 900001;
DELETE FROM user_items WHERE user_id >= 900001;
DELETE FROM item_outbox_events WHERE aggregate_id >= 900001;
COMMIT;


-- =========================================================================
-- [5] EVENT LOG (이벤트 로그 수집) 데이터베이스 세션에서 실행
-- =========================================================================
BEGIN;
DELETE FROM event_logs WHERE user_id >= 900001;
COMMIT;


-- =========================================================================
-- [6] NOTIFICATION (푸시 알림) 데이터베이스 세션에서 실행
-- =========================================================================
BEGIN;
DELETE FROM notification_push_deliveries WHERE user_id >= 900001;
DELETE FROM notifications WHERE user_id >= 900001;
DELETE FROM fcm_device_tokens WHERE user_id >= 900001;
DELETE FROM notification_settings WHERE user_id >= 900001;
COMMIT;
