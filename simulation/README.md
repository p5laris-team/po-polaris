# Polaris Load And Chaos Test

This directory is the reproducible evidence package for testing-strategy stage 4.

## Quick Start

```powershell
npm.cmd run simulation:data
npm.cmd run simulation:validate
npm.cmd run simulation:smoke
```

`simulation:smoke` starts the AI mock server, runs k6 in Docker, verifies normal,
error, malformed response, timeout, and recovery paths, then stops the mock
server. The JSON result is written to
`simulation/results/k6-ai-mock-smoke-summary.json`.

## Full Service Scenario

Start the Polaris services and seed the generated SQL before running:

```powershell
docker run --rm --add-host=host.docker.internal:host-gateway `
  -e GATEWAY_URL=http://host.docker.internal:8080 `
  -v "${PWD}/simulation:/scripts" `
  grafana/k6:0.54.0 run /scripts/k6-simulation-script.js
```

The full scenario covers token issuance, onboarding, character creation, AI
talk, mission completion, sharing rewards, and duplicate item purchases.

## Chaos Injection

AI chaos is the safe default and always resets in a `finally` block:

```powershell
.\simulation\inject-chaos.ps1 -Target ai -AiChaosType timeout -DurationSeconds 10
```

Redis or Kafka interruption requires explicit consent:

```powershell
.\simulation\inject-chaos.ps1 -Target redis -DurationSeconds 10 -AllowContainerRestart
.\simulation\inject-chaos.ps1 -Target kafka -DurationSeconds 10 -AllowContainerRestart
```

## Portfolio Report

Record each run with this table:

| Field | Evidence |
|---|---|
| Scenario purpose | Validate critical user flow and AI fallback/recovery |
| Target throughput | VUs, iterations, and expected requests per second |
| Observed latency | `http_req_duration` average and p95 |
| Error rate | `http_req_failed` and failed checks |
| Bottleneck | Slow endpoint, dependency, DB lock, or broker backlog |
| Recovery | Time until normal response after chaos is cleared |
| Improvement | Concrete timeout, retry, cache, query, or capacity action |

Cleanup should be executed only after reviewing the verification queries in
`verify-and-cleanup.sql`. The synthetic user ID range starts at `900001`.
