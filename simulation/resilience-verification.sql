-- =========================================================================
-- Polaris Kafka/Payment resilience verification SQL
--
-- 목적:
-- - k6 부하 테스트와 chaos injection 이후 복구 결과를 수치로 확인한다.
-- - 각 SELECT 결과가 0 rows이면 중복 처리, 잔액 불일치, 미처리 outbox가 남지 않은 상태로 판단한다.
--
-- 실행 방식:
-- - 마이크로서비스별 DB가 분리되어 있으므로 DBeaver 등에서 해당 DB에 접속한 뒤
--   필요한 섹션만 선택 실행한다.
-- - 검증 대상은 simulation 데이터셋의 user_id >= 900001 범위다.
-- =========================================================================

-- =========================================================================
-- [1] USER DB: wallet/payment/idempotency 검증
-- =========================================================================

-- 1-1. Kafka at-least-once 중복 delivery로 같은 멱등키가 2회 이상 원장에 적재되었는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    idempotency_key,
    COUNT(*) AS duplicated_count
FROM star_piece_transactions
WHERE idempotency_key IS NOT NULL
  AND user_id >= 900001
GROUP BY idempotency_key
HAVING COUNT(*) > 1;

-- 1-2. 지갑 잔액과 별조각 원장 합계가 어긋났는지 확인한다.
-- simulation 생성 유저의 초기 별조각은 500으로 둔다.
-- 기대 결과: 0 rows
SELECT
    w.user_id,
    w.star_piece AS wallet_balance,
    500 + COALESCE(SUM(t.amount), 0) AS calculated_balance
FROM wallets w
LEFT JOIN star_piece_transactions t ON t.user_id = w.user_id
WHERE w.user_id >= 900001
GROUP BY w.user_id, w.star_piece
HAVING w.star_piece <> 500 + COALESCE(SUM(t.amount), 0);

-- 1-3. 동일 결제 주문에 payment transaction이 2건 이상 생성되었는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    po.order_no,
    COUNT(pt.id) AS transaction_count
FROM payment_orders po
JOIN payment_transactions pt ON pt.payment_order_id = po.id
WHERE po.user_id >= 900001
GROUP BY po.order_no
HAVING COUNT(pt.id) > 1;

-- 1-4. 결제 완료 주문인데 PAYMENT:{orderNo} 지갑 충전 원장이 없거나 중복 생성되었는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    po.order_no,
    po.status,
    COUNT(spt.id) AS wallet_charge_count
FROM payment_orders po
LEFT JOIN star_piece_transactions spt
       ON spt.idempotency_key = CONCAT('PAYMENT:', po.order_no)
WHERE po.user_id >= 900001
  AND po.status = 'PAID'
GROUP BY po.order_no, po.status
HAVING COUNT(spt.id) <> 1;

-- 1-5. user outbox가 Kafka 복구 이후에도 미처리 상태로 남았는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    status,
    COUNT(*) AS remaining_count
FROM user_outbox_events
WHERE status <> 'SUCCEEDED'
GROUP BY status;

-- =========================================================================
-- [2] ITEM DB: item purchase saga 검증
-- =========================================================================

-- 2-1. 동일 구매 멱등키가 item 구매 이력에 2건 이상 생성되었는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    idempotency_key,
    COUNT(*) AS duplicated_count
FROM item_purchase_histories
WHERE idempotency_key IS NOT NULL
  AND user_id >= 900001
GROUP BY idempotency_key
HAVING COUNT(*) > 1;

-- 2-2. Kafka 복구 이후 item outbox 미처리 이벤트가 남았는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    status,
    COUNT(*) AS remaining_count
FROM item_outbox_events
WHERE status <> 'SUCCEEDED'
GROUP BY status;

-- =========================================================================
-- [3] MISSION DB: mission reward outbox 검증
-- =========================================================================

-- 3-1. Kafka 복구 이후 mission reward outbox 미처리 이벤트가 남았는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    status,
    COUNT(*) AS remaining_count
FROM mission_outbox_events
WHERE status <> 'SUCCEEDED'
GROUP BY status;

-- =========================================================================
-- [4] CHARACTER DB: share reward/notification outbox 검증
-- =========================================================================

-- 4-1. 공유 보상이 같은 사용자/날짜에 2회 이상 지급되었는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    user_id,
    share_date,
    COUNT(*) AS paid_count
FROM share_logs
WHERE reward_paid = true
  AND user_id >= 900001
GROUP BY user_id, share_date
HAVING COUNT(*) > 1;

-- 4-2. Kafka 복구 이후 character outbox 미처리 이벤트가 남았는지 확인한다.
-- 기대 결과: 0 rows
SELECT
    status,
    COUNT(*) AS remaining_count
FROM character_outbox_events
WHERE status <> 'SUCCEEDED'
GROUP BY status;
