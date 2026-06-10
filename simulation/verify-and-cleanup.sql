-- =========================================================================
-- Polaris 부하/장애 시뮬레이션 결과 검증 및 전체 마이크로서비스 클린업 SQL
-- 
-- [안내] 마이크로서비스별로 물리 DB(또는 스키마)가 완전히 격리 분리되어 있으므로,
-- DBeaver 등의 GUI 도구에서 각 데이터베이스 세션(커넥션)에 개별 접속한 뒤
-- 해당하는 섹션의 쿼리 블록만 드래그 지정하여 순차 실행하십시오.
-- =========================================================================

-- =========================================================================
-- [1] USER (회원 / 재화 / 지갑) 데이터베이스 세션에서 실행
-- =========================================================================

-- 1-1) 분산 트랜잭션(Saga) 멱등키 위반 및 보상 중복 수령 검사
-- - 결과 행(Row) 수가 0건이어야 동시성 하에 멱등성이 보장된 것입니다.
SELECT idempotency_key, COUNT(*) 
FROM star_piece_transactions 
WHERE idempotency_key IS NOT NULL 
GROUP BY idempotency_key 
HAVING COUNT(*) > 1;

-- 1-2) 재화 거래원장 합계액과 실제 지갑 잔액 불일치(정합성) 검사
-- - 결과 행이 0건이어야 시뮬레이션 중 분산 락 및 차감 정합성이 깨지지 않은 것입니다.
-- - 가상 유저 기본 재화 500스타피스 기준에 Earn/Spend 내역을 대조 연산합니다.
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
-- - 외래키 제약조건에 맞춰 payment_transactions를 payment_orders를 거쳐 서브쿼리로 참조 삭제하도록 수정되었습니다.
-- - user_outbox_events의 복원도 정상 반영되었습니다.
BEGIN;
DELETE FROM star_piece_transactions WHERE user_id >= 900001;
DELETE FROM wallets WHERE user_id >= 900001;
DELETE FROM onboarding_profiles WHERE user_id >= 900001;
DELETE FROM attendance_records WHERE user_id >= 900001;
DELETE FROM payment_transactions WHERE payment_order_id IN (SELECT id FROM payment_orders WHERE user_id >= 900001);
DELETE FROM payment_orders WHERE user_id >= 900001;
DELETE FROM user_outbox_events WHERE aggregate_id >= 900001;
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

-- 3-1) 공유 보상 사용자별 하루 1회 수령 제한 비즈니스 룰 검사
-- - 하루에 reward_paid가 여러 개 지급된 부당 지급 케이스가 있는지 검사합니다 (결과 0건 정상).
SELECT user_id, share_date, COUNT(*) 
FROM share_logs 
WHERE reward_paid = true 
GROUP BY user_id, share_date 
HAVING COUNT(*) > 1;

-- 3-2) 가상 테스트 데이터 삭제 (롤백)
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


-- =========================================================================
-- [7] AI (AI 생성 및 사용 로그) 데이터베이스 세션에서 실행
-- =========================================================================

-- 7-1) AI Mock 장애 주입에 따른 오류 로그(Fallback 발동 전적) 모니터링
-- - 카오스(Chaos) 주입 기간 동안 FAIL 또는 TIMEOUT 에러가 성공적으로 기록되었는지 수량 확인
SELECT status, error_type, COUNT(*) 
FROM ai_usage_logs 
WHERE user_id >= 900001 AND (status = 'FAIL' OR error_type IS NOT NULL)
GROUP BY status, error_type;

-- 7-2) AI 미션 Fallback 사용 비율 모니터링 (서킷 브레이커 오프라인 우회 비율 검증)
SELECT fallback_used, COUNT(*) 
FROM ai_mission_generations 
WHERE user_id >= 900001
GROUP BY fallback_used;

-- 7-3) 가상 테스트 데이터 삭제 (롤백)
BEGIN;
DELETE FROM ai_usage_logs WHERE user_id >= 900001;
DELETE FROM ai_mission_generations WHERE user_id >= 900001;
COMMIT;
