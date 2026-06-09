# Polaris Multi-Service DB Batch Cleanup Automation Script (Docker exec psql version)
# Usage: powershell -ExecutionPolicy Bypass -File ./simulation/cleanup-databases.ps1

# 한글 깨짐 방지를 위한 콘솔 및 출력 인코딩 설정 (UTF-8)
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Polaris Multi-DB Cleanup Starting (Docker)..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# ── [설정] 본인의 DB 접속 정보 및 서비스별 데이터베이스명을 입력하세요 ──
# - CONTAINER_NAME: 데이터베이스가 기동 중인 Docker 컨테이너명
# - DB_USER / DB_PASS: PostgreSQL의 계정 정보
$CONTAINER_NAME = "postgres-compose"
$DB_USER        = "root"
$DB_PASS        = "12345678"

# 각 마이크로서비스용 개별 데이터베이스명
$DB_USER_SERVICE     = "users"
$DB_MISSION_SERVICE  = "mission"
$DB_CHAR_SERVICE     = "character"
$DB_ITEM_SERVICE     = "item"
$DB_LOG_SERVICE      = "event_log"
$DB_NOTIF_SERVICE    = "notification"
$DB_AI_SERVICE       = "ai"
# ───────────────────────────────────────────────────────────

# 1. 도커 컨테이너가 로컬 PC에서 켜져 있는지 확인
$containerStatus = docker ps --filter "name=$CONTAINER_NAME" --format "{{.Status}}"
if (-not $containerStatus) {
    Write-Host "[ERROR] Docker container '$CONTAINER_NAME' is not running." -ForegroundColor Red
    Write-Host "-> Please start the database container using 'docker-compose' first." -ForegroundColor DarkYellow
    Exit 1
}

Write-Host "-> Detected container: '$CONTAINER_NAME' (Running)" -ForegroundColor Green

# 2. 공통 쿼리 실행 함수 (도커 내부 psql 호출)
# - 파워쉘 파서 오류 및 '=' 기호 해석 오류를 우회하기 위해 인자 배열(Splatting)을 사용하여 docker exec을 호출합니다.
function Execute-Docker-Query {
    param(
        [string]$dbName,
        [string]$sqlCommand
    )
    Write-Host "-> [$dbName] Database cleaning in progress..." -ForegroundColor Yellow
    
    # docker exec 명령어 전달 시 파워쉘 구문 꼬임을 원천 방어하기 위한 인수 구성
    $dockerArgs = @(
        "exec", "-i",
        "-e", "PGPASSWORD=$DB_PASS",
        $CONTAINER_NAME,
        "psql", "-U", $DB_USER,
        "-d", $dbName,
        "-c", $sqlCommand
    )
    
    # 외부 프로세스(Docker) 실행 및 표준 에러를 표준 출력으로 통합하여 대입
    $output = & docker $dockerArgs 2>&1
    
    # $LASTEXITCODE를 통하여 psql 실행에 오류가 없었는지 감지
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   [SUCCESS] Cleanup completed!" -ForegroundColor Green
    } else {
        Write-Host "   [ERROR] Failed to execute query." -ForegroundColor Red
        Write-Host "   Details: $output" -ForegroundColor DarkRed
    }
}

# ───────────────────────────────────────────────────────────
# 3. 마이크로서비스별 데이터 청소 수행 (참조 제약 조건을 고려하여 의존 관계가 있는 테이블 우선 삭제)
# ───────────────────────────────────────────────────────────

# 3-1) NOTIFICATION 서비스 데이터 삭제
# - FCM 토큰, 알림 메시지, 수령 대상 정보 중 시뮬레이션 테스트 유저 ID(>=900001)에 해당하는 내역 제거
$notifSql = "BEGIN; DELETE FROM notification_push_deliveries WHERE user_id >= 900001; DELETE FROM notifications WHERE user_id >= 900001; DELETE FROM fcm_device_tokens WHERE user_id >= 900001; DELETE FROM notification_settings WHERE user_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_NOTIF_SERVICE $notifSql

# 3-2) EVENT LOG 서비스 데이터 삭제
$logSql = "BEGIN; DELETE FROM event_logs WHERE user_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_LOG_SERVICE $logSql

# 3-3) ITEM 서비스 데이터 삭제
# - 아이템 사용 이력, 구매 이력을 지운 뒤 보유 아이템 및 아웃박스 이벤트 청소
$itemSql = "BEGIN; DELETE FROM item_usage_histories WHERE user_id >= 900001; DELETE FROM item_purchase_histories WHERE user_id >= 900001; DELETE FROM user_items WHERE user_id >= 900001; DELETE FROM item_outbox_events; COMMIT;"
Execute-Docker-Query $DB_ITEM_SERVICE $itemSql

# 3-4) CHARACTER / SHARE 서비스 데이터 삭제
# - 캐릭터 육성 로그(Exp, Care), SNS 공유 기록, 공유 카드 및 캐릭터 아웃박스 이벤트 청소
$charSql = "BEGIN; DELETE FROM share_logs WHERE user_id >= 900001; DELETE FROM share_cards WHERE user_id >= 900001; DELETE FROM character_exp_logs WHERE user_id >= 900001; DELETE FROM character_care_logs WHERE user_id >= 900001; DELETE FROM user_characters WHERE user_id >= 900001; DELETE FROM character_outbox_events; COMMIT;"
Execute-Docker-Query $DB_CHAR_SERVICE $charSql

# 3-5) MISSION 서비스 데이터 삭제
# - RAG 기억 단편(user_memories), 미션 피드백/답변, 일일 배정 미션(user_missions) 데이터 제거
$missionSql = "BEGIN; DELETE FROM user_memories WHERE user_id >= 900001; DELETE FROM mission_feedbacks WHERE user_id >= 900001; DELETE FROM mission_completion_answers WHERE user_id >= 900001; DELETE FROM user_missions WHERE user_id >= 900001; DELETE FROM mission_outbox_events; COMMIT;"
Execute-Docker-Query $DB_MISSION_SERVICE $missionSql

# 3-6) USER 서비스 데이터 삭제
# - 외래키 오류 방지를 위해 하위 참조 데이터(star_piece, wallets, onboarding_profiles, attendance, payment_transactions, payment_orders)를 먼저 순차 삭제한 후, 최종적으로 users 테이블에서 시뮬레이션용 유저 데이터를 지웁니다.
# - 결제(payment_transactions)는 외래키 제약조건에 맞춰 payment_orders를 거쳐 서브쿼리로 참조 삭제하도록 수정 반영되었습니다.
$userSql = "BEGIN; DELETE FROM star_piece_transactions WHERE user_id >= 900001; DELETE FROM wallets WHERE user_id >= 900001; DELETE FROM onboarding_profiles WHERE user_id >= 900001; DELETE FROM attendance_records WHERE user_id >= 900001; DELETE FROM payment_transactions WHERE payment_order_id IN (SELECT id FROM payment_orders WHERE user_id >= 900001); DELETE FROM payment_orders WHERE user_id >= 900001; DELETE FROM user_outbox_events; DELETE FROM users WHERE id >= 900001 OR email LIKE 'simulation_test_%'; COMMIT;"
Execute-Docker-Query $DB_USER_SERVICE $userSql

# 3-7) AI 서비스 데이터 삭제
$aiSql = "BEGIN; DELETE FROM ai_usage_logs WHERE user_id >= 900001; DELETE FROM ai_mission_generations WHERE user_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_AI_SERVICE $aiSql

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host " Polaris Multi-DB Cleanup Completed!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
