# AI Mock k6 Smoke Result

- Run date: 2026-06-12
- Runtime: `grafana/k6:0.54.0`
- Scenario: normal response, 400 error, malformed JSON, timeout, reset, recovery
- Load shape: 1 VU, 1 iteration, 10 HTTP requests

## Result

| Metric | Observed | Threshold | Status |
|---|---:|---:|---|
| Checks | 12/12 (100%) | 100% | Pass |
| HTTP error rate | 0% | 0% | Pass |
| Average latency | 30.23 ms | Informational | Pass |
| p95 latency | 154.20 ms | < 1,000 ms | Pass |
| Maximum latency | 260.28 ms | Informational | Pass |
| Recovery | First request after reset returned 200 | Required | Pass |

Expected 400 and 504 chaos responses are registered as expected statuses, so
they do not inflate the transport error rate. The timeout mock was configured
to 250 ms for a fast smoke test.

## Interpretation

The smoke run proves that the mock and chaos controls are executable, malformed
payloads are detectable, timeout behavior is measurable, and normal service
returns immediately after chaos reset. It does not measure production capacity
or database/Kafka bottlenecks.

The next full-stack run should use `k6-simulation-script.js`, record sustained
RPS and p95 latency, interrupt Redis and Kafka separately, and correlate the
result with service metrics, outbox backlog, and recovery time.
