# Polaris 부하/장애 시뮬레이션용 Chaos 주입 파워쉘 스크립트
# 실행법: .\inject-chaos.ps1

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Polaris Chaos Injection Script Starting..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Redis 장애 주입 (Redis 컨테이너 일시 중지 후 복구)
Write-Host "`n[1/2] Redis 장애 주입 시작..." -ForegroundColor Magenta

# 실행 중인 Redis 도커 컨테이너명 동적 감지
$redisContainer = docker ps --filter "ancestor=redis" --format "{{.Names}}" | Select-Object -First 1
if (-not $redisContainer) {
    $redisContainer = docker ps --filter "name=redis" --format "{{.Names}}" | Select-Object -First 1
}

if ($redisContainer) {
    Write-Host "-> 감지된 Redis 컨테이너: '$redisContainer'" -ForegroundColor Green
    Write-Host "-> Redis 컨테이너를 일시 중지합니다..." -ForegroundColor Yellow
    docker stop $redisContainer
    
    Write-Host "-> 15초 대기하며 시스템(Rate Limiter Fallback) 상태를 검증합니다..." -ForegroundColor Cyan
    Start-Sleep -Seconds 15
    
    Write-Host "-> Redis 컨테이너를 다시 시작하여 복구합니다..." -ForegroundColor Green
    docker start $redisContainer
} else {
    Write-Host "-> [경고] 로컬에서 기동 중인 Redis 도커 컨테이너를 찾지 못했습니다. Redis 컨테이너 장애 주입을 건너뜁니다." -ForegroundColor DarkYellow
    Write-Host "-> (로컬 Redis가 Docker가 아닌 네이티브 서비스로 가동 중이거나 다른 이름일 수 있습니다.)" -ForegroundColor Gray
}

# 2. AI Mock 서버 장애 주입 (Stateful Chaos API 호출)
Write-Host "`n[2/2] AI Mock 서버 장애 주입 시작..." -ForegroundColor Magenta

try {
    Write-Host "-> AI Mock 서버에 전역 'timeout' (504 Gateway Timeout) 장애 상태를 주입합니다..." -ForegroundColor Yellow
    $resTimeout = Invoke-RestMethod -Uri "http://localhost:8085/chaos/inject?type=timeout" -Method Get
    Write-Host "-> 응답 결과: $($resTimeout.message)" -ForegroundColor Green
    
    Write-Host "-> 10초간 장애 상태를 유지합니다. 백엔드 AI 모듈의 Fallback(로컬 룰 기반 미션 변환) 작동 여부를 확인해 보세요." -ForegroundColor Cyan
    Start-Sleep -Seconds 10
    
    Write-Host "-> AI Mock 서버의 장애 상태를 해제(복구)합니다..." -ForegroundColor Green
    $resNone = Invoke-RestMethod -Uri "http://localhost:8085/chaos/inject?type=none" -Method Get
    Write-Host "-> 응답 결과: $($resNone.message)" -ForegroundColor Green
} catch {
    Write-Host "-> [에러] AI Mock 서버(http://localhost:8085) 통신 실패. 서버가 구동 중인지 확인하세요." -ForegroundColor Red
    Write-Host "-> 에러 내용: $_" -ForegroundColor DarkRed
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host " Polaris Chaos Injection Completed!" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
