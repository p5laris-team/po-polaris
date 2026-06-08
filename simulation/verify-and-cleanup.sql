-- ==========================================================
-- Polaris 부하/장애 시뮬레이션 결과 검증 및 클린업 SQL 스크립트
-- 기존 개발 DB(5432)에 접속 후 수행하십시오.
-- ==========================================================

-- [1] 멱등키 위반 및 보상 중복 수령 검사
-- 결과 행이 0건이어야 멱등성이 보장된 것입니다.
SELECT idempotency_key, COUNT(*) 
FROM star_piece_transactions 
WHERE idempotency_key IS NOT NULL 
GROUP BY idempotency_key 
HAVING COUNT(*) > 1;


-- [2] 거래원장 합계액과 실제 지갑 잔액 불일치 검사
-- (초기 재화 500개 지급 기준 + EARN/SPEND 합산 결과와 실제 wallets 테이블 잔액 비교)
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


-- [3] 가상 시뮬레이션용 데이터 일괄 삭제 (개발 DB 롤백 원복)
BEGIN;

-- A. 지갑 트랜잭션 기록 삭제
DELETE FROM star_piece_transactions WHERE user_id >= 900001;

-- B. 지갑 레코드 삭제
DELETE FROM wallets WHERE user_id >= 900001;

-- C. 온보딩 프로필 레코드 삭제
DELETE FROM onboarding_profiles WHERE user_id >= 900001;

-- D. 유저 정보 및 메인 계정 삭제 (simulation_test_ 접두사 및 900001 이상 대역 일괄 정제)
DELETE FROM users WHERE id >= 900001 OR email LIKE 'simulation_test_%';

COMMIT;
