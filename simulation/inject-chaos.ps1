param(
    [ValidateSet("ai", "redis", "kafka", "all")]
    [string]$Target = "ai",

    [ValidateSet("timeout", "bad_request", "broken_json")]
    [string]$AiChaosType = "timeout",

    [int]$DurationSeconds = 10,
    [string]$AiMockUrl = "http://localhost:8085",
    [switch]$AllowContainerRestart
)

$ErrorActionPreference = "Stop"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-AiChaos {
    Write-Host "Injecting AI chaos '$AiChaosType' for $DurationSeconds seconds."
    try {
        Invoke-RestMethod -Uri "$AiMockUrl/chaos/inject?type=$AiChaosType" -Method Get | Out-Null
        Start-Sleep -Seconds $DurationSeconds
    }
    finally {
        Invoke-RestMethod -Uri "$AiMockUrl/chaos/inject?type=none" -Method Get | Out-Null
        Write-Host "AI chaos state restored to normal."
    }
}

function Find-Container([string[]]$Filters) {
    foreach ($filter in $Filters) {
        $container = docker ps --filter $filter --format "{{.Names}}" | Select-Object -First 1
        if ($container) {
            return $container
        }
    }
    return $null
}

function Invoke-ContainerChaos([string]$Component, [string[]]$Filters) {
    if (-not $AllowContainerRestart) {
        throw "Container chaos requires -AllowContainerRestart. Target: $Component"
    }

    $container = Find-Container $Filters
    if (-not $container) {
        Write-Warning "No active $Component container found. Skipping."
        return
    }

    Write-Host "Stopping $Component container '$container' for $DurationSeconds seconds."
    try {
        docker stop $container | Out-Null
        Start-Sleep -Seconds $DurationSeconds
    }
    finally {
        docker start $container | Out-Null
        Write-Host "$Component container '$container' restored."
    }
}

if ($Target -in @("ai", "all")) {
    Invoke-AiChaos
}
if ($Target -in @("redis", "all")) {
    Invoke-ContainerChaos "Redis" @("ancestor=redis", "name=redis")
}
if ($Target -in @("kafka", "all")) {
    Invoke-ContainerChaos "Kafka" @("name=kafka-broker", "name=kafka", "ancestor=kafka")
}

Write-Host "Chaos scenario completed and restoration was attempted."
