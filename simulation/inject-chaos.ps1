# Polaris Chaos Injection Script (Redis & AI Mock Server)
# Usage: powershell -ExecutionPolicy Bypass -File ./simulation/inject-chaos.ps1

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Polaris Chaos Injection Script Starting..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Redis Chaos Injection (Stop and Restart Redis Container)
Write-Host "`n[1/2] Starting Redis Chaos Injection..." -ForegroundColor Magenta

# Detect running Redis container name dynamically
$redisContainer = docker ps --filter "ancestor=redis" --format "{{.Names}}" | Select-Object -First 1
if (-not $redisContainer) {
    $redisContainer = docker ps --filter "name=redis" --format "{{.Names}}" | Select-Object -First 1
}

if ($redisContainer) {
    Write-Host "-> Detected Redis container: '$redisContainer'" -ForegroundColor Green
    Write-Host "-> Stopping Redis container..." -ForegroundColor Yellow
    docker stop $redisContainer
    
    Write-Host "-> Waiting 15s to verify system resilience (Rate Limiter Fallback)..." -ForegroundColor Cyan
    Start-Sleep -Seconds 15
    
    Write-Host "-> Starting Redis container to restore..." -ForegroundColor Green
    docker start $redisContainer
} else {
    Write-Host "-> [WARNING] Active Redis container not found. Skipping Redis chaos injection." -ForegroundColor DarkYellow
    Write-Host "-> (Local Redis may be running natively or under a different container name.)" -ForegroundColor Gray
}

# 2. AI Mock Server Chaos Injection (Stateful Chaos API Call)
Write-Host "`n[2/2] Starting AI Mock Server Chaos Injection..." -ForegroundColor Magenta

try {
    Write-Host "-> Injecting global 'timeout' (504 Gateway Timeout) chaos state into AI Mock Server..." -ForegroundColor Yellow
    $resTimeout = Invoke-RestMethod -Uri "http://localhost:8085/chaos/inject?type=timeout" -Method Get
    Write-Host "-> Response: $($resTimeout.message)" -ForegroundColor Green
    
    Write-Host "-> Keeping chaos state active for 10s. Monitor if Backend Fallback triggers." -ForegroundColor Cyan
    Start-Sleep -Seconds 10
    
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
