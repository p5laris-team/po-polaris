# Polaris Chaos Injection Script (Redis & AI Mock Server)
# Usage: powershell -ExecutionPolicy Bypass -File ./simulation/inject-chaos.ps1

# 콘솔 출력 및 파이프 인코딩 설정 (UTF-8)
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Polaris Chaos Injection Script Starting..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# ───────────────────────────────────────────────────────────
# 1. Redis Chaos Injection (Stop and Restart Redis Container)
# ───────────────────────────────────────────────────────────
Write-Host "`n[1/2] Starting Redis Chaos Injection..." -ForegroundColor Magenta

# 로컬에서 구동 중인 Redis 도커 컨테이너명을 동적으로 조회하여 감지
$redisContainer = docker ps --filter "ancestor=redis" --format "{{.Names}}" | Select-Object -First 1
if (-not $redisContainer) {
    # 조회가 되지 않을 경우 컨테이너 이름에 'redis'가 들어간 대상을 차선책으로 검색
    $redisContainer = docker ps --filter "name=redis" --format "{{.Names}}" | Select-Object -First 1
}

if ($redisContainer) {
    Write-Host "-> Detected Redis container: '$redisContainer'" -ForegroundColor Green
    
    # Redis 컨테이너를 일시 중지하여 백엔드의 처리율 제한(Rate Limiter) 장애를 강제 유발
    Write-Host "-> Stopping Redis container..." -ForegroundColor Yellow
    docker stop $redisContainer
    
    # 15초 동안 셧다운 상태를 유지하며 백엔드 게이트웨이가 로컬 인메모리 fallback 정책을 정상 가동하는지 검증
    Write-Host "-> Waiting 15s to verify system resilience (Rate Limiter Fallback)..." -ForegroundColor Cyan
    Start-Sleep -Seconds 15
    
    # 검증 완료 후, 정지되었던 Redis 컨테이너를 다시 구동하여 정상 복구
    Write-Host "-> Starting Redis container to restore..." -ForegroundColor Green
    docker start $redisContainer
} else {
    Write-Host "-> [WARNING] Active Redis container not found. Skipping Redis chaos injection." -ForegroundColor DarkYellow
    Write-Host "-> (Local Redis may be running natively or under a different container name.)" -ForegroundColor Gray
}

# ───────────────────────────────────────────────────────────
# 2. AI Mock Server Chaos Injection (Stateful Chaos API Call)
# ───────────────────────────────────────────────────────────
Write-Host "`n[2/2] Starting AI Mock Server Chaos Injection..." -ForegroundColor Magenta

try {
    # AI Mock 서버의 상태 제어 API를 호출하여 5초 지연 및 504 Gateway Timeout 강제 응답 상태 주입
    Write-Host "-> Injecting global 'timeout' (504 Gateway Timeout) chaos state into AI Mock Server..." -ForegroundColor Yellow
    $resTimeout = Invoke-RestMethod -Uri "http://localhost:8085/chaos/inject?type=timeout" -Method Get
    Write-Host "-> Response: $($resTimeout.message)" -ForegroundColor Green
    
    # 10초간 AI 장애 상태를 유지하여, 백엔드 AI 모듈의 서킷 브레이커 작동 및 로컬 룰 미션 Fallback 전환 검증
    Write-Host "-> Keeping chaos state active for 10s. Monitor if Backend Fallback triggers." -ForegroundColor Cyan
    Start-Sleep -Seconds 10
    
    # 시뮬레이션 검증 완료 후, 다시 AI Mock 서버를 정상 응답 모드로 복구
    Write-Host "-> Clearing chaos state from AI Mock Server..." -ForegroundColor Green
    $resNone = Invoke-RestMethod -Uri "http://localhost:8085/chaos/inject?type=none" -Method Get
    Write-Host "-> Response: $($resNone.message)" -ForegroundColor Green
} catch {
    Write-Host "-> [ERROR] Failed to communicate with AI Mock Server at http://localhost:8085. Ensure it is running." -ForegroundColor Red
    Write-Host "-> Error details: $_" -ForegroundColor DarkRed
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host " Polaris Chaos Injection Completed!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
