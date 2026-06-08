# Polaris Multi-Service DB Batch Cleanup Automation Script (Docker exec psql version)
# Usage: powershell -ExecutionPolicy Bypass -File ./simulation/cleanup-databases.ps1

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Polaris Multi-DB Cleanup Starting (Docker)..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# ── [Settings] DB connection parameters and database names ──
$CONTAINER_NAME = "postgres-compose"
$DB_USER        = "root"
$DB_PASS        = "12345678"

$DB_USER_SERVICE     = "users"
$DB_MISSION_SERVICE  = "mission"
$DB_CHAR_SERVICE     = "character"
$DB_ITEM_SERVICE     = "item"
$DB_LOG_SERVICE      = "event_log"
$DB_NOTIF_SERVICE    = "notification"
$DB_AI_SERVICE       = "ai"
# ───────────────────────────────────────────────────────────

# 1. Check if the docker container is running
$containerStatus = docker ps --filter "name=$CONTAINER_NAME" --format "{{.Status}}"
if (-not $containerStatus) {
    Write-Host "[ERROR] Docker container '$CONTAINER_NAME' is not running." -ForegroundColor Red
    Write-Host "-> Please start the database container using 'docker-compose' first." -ForegroundColor DarkYellow
    Exit 1
}

Write-Host "-> Detected container: '$CONTAINER_NAME' (Running)" -ForegroundColor Green

# Common query execution function using docker exec psql
function Execute-Docker-Query {
    param(
        [string]$dbName,
        [string]$sqlCommand
    )
    Write-Host "-> [$dbName] Database cleaning in progress..." -ForegroundColor Yellow
    
    $dockerArgs = @(
        "exec", "-i",
        "-e", "PGPASSWORD=$DB_PASS",
        $CONTAINER_NAME,
        "psql", "-U", $DB_USER,
        "-d", $dbName,
        "-c", $sqlCommand
    )
    
    $output = & docker $dockerArgs 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   [SUCCESS] Cleanup completed!" -ForegroundColor Green
    } else {
        Write-Host "   [ERROR] Failed to execute query." -ForegroundColor Red
        Write-Host "   Details: $output" -ForegroundColor DarkRed
    }
}

# 1. NOTIFICATION
$notifSql = "BEGIN; DELETE FROM notification_push_deliveries WHERE user_id >= 900001; DELETE FROM notifications WHERE user_id >= 900001; DELETE FROM fcm_device_tokens WHERE user_id >= 900001; DELETE FROM notification_settings WHERE user_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_NOTIF_SERVICE $notifSql

# 2. EVENT LOG
$logSql = "BEGIN; DELETE FROM event_logs WHERE user_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_LOG_SERVICE $logSql

# 3. ITEM
$itemSql = "BEGIN; DELETE FROM item_usage_histories WHERE user_id >= 900001; DELETE FROM item_purchase_histories WHERE user_id >= 900001; DELETE FROM user_items WHERE user_id >= 900001; DELETE FROM item_outbox_events WHERE aggregate_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_ITEM_SERVICE $itemSql

# 4. CHARACTER / SHARE
$charSql = "BEGIN; DELETE FROM share_logs WHERE user_id >= 900001; DELETE FROM share_cards WHERE user_id >= 900001; DELETE FROM character_exp_logs WHERE user_id >= 900001; DELETE FROM character_care_logs WHERE user_id >= 900001; DELETE FROM user_characters WHERE user_id >= 900001; DELETE FROM character_outbox_events WHERE aggregate_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_CHAR_SERVICE $charSql

# 5. MISSION
$missionSql = "BEGIN; DELETE FROM user_memories WHERE user_id >= 900001; DELETE FROM mission_feedbacks WHERE user_id >= 900001; DELETE FROM mission_completion_answers WHERE user_id >= 900001; DELETE FROM user_missions WHERE user_id >= 900001; DELETE FROM mission_outbox_events WHERE aggregate_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_MISSION_SERVICE $missionSql

# 6. USER
$userSql = "BEGIN; DELETE FROM star_piece_transactions WHERE user_id >= 900001; DELETE FROM wallets WHERE user_id >= 900001; DELETE FROM onboarding_profiles WHERE user_id >= 900001; DELETE FROM attendance_records WHERE user_id >= 900001; DELETE FROM payment_transactions WHERE payment_order_id IN (SELECT id FROM payment_orders WHERE user_id >= 900001); DELETE FROM payment_orders WHERE user_id >= 900001; DELETE FROM user_outbox_events WHERE aggregate_id >= 900001; DELETE FROM users WHERE id >= 900001 OR email LIKE 'simulation_test_%'; COMMIT;"
Execute-Docker-Query $DB_USER_SERVICE $userSql

# 7. AI
$aiSql = "BEGIN; DELETE FROM ai_usage_logs WHERE user_id >= 900001; DELETE FROM ai_mission_generations WHERE user_id >= 900001; COMMIT;"
Execute-Docker-Query $DB_AI_SERVICE $aiSql

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host " Polaris Multi-DB Cleanup Completed!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
