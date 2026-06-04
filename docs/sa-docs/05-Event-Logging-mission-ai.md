# MISSION / AI 이벤트 로그 수집 정의

---

## 1. 공통 원칙

MISSION / AI 모듈의 이벤트 로그는 제품 지표와 개인화 품질 분석을 위해 수집한다.

로그 저장은 각 도메인의 핵심 트랜잭션이 성공적으로 커밋된 뒤 비동기로 수행한다. event-log 모듈 호출에 실패해도 미션 생성, 미션 완료, AI 문구 생성 같은 메인 로직 실패로 전파하지 않는다.

### 저장 방식

```text
도메인 서비스 트랜잭션 성공
→ ApplicationEvent 발행
→ @TransactionalEventListener(phase = AFTER_COMMIT)
→ @Async
→ event-log gRPC recordEventLog 호출
```

### gRPC 필드 기준

`RecordEventLogRequest` 기준으로 아래 필드를 사용한다.

| 필드 | 설명 |
| --- | --- |
| `eventId` | 이벤트 중복 저장 방지용 UUID |
| `eventType` | 이벤트 타입 |
| `sourceService` | 이벤트 발생 모듈. 예: `mission`, `ai` |
| `userId` | 로그인 사용자 ID |
| `refType` | 이벤트가 참조하는 대상 타입 |
| `refId` | 이벤트가 참조하는 대상 ID |
| `propertiesJson` | 이벤트 자체의 분석 속성 |
| `contextJson` | 화면, 디바이스, 앱 버전 등 발생 환경. 서버 내부 이벤트에서는 생략 가능 |
| `occurredAt` | 이벤트가 실제 발생한 시각 |

### 민감 정보 기준

아래 값은 event-log에 저장하지 않는다.

```text
사용자 완료 답변 전문
AI raw prompt
AI raw response
accessToken / refreshToken / OAuth code
idempotencyKey 원문
대용량 JSON 전문
```

중복 요청 분석이 필요한 경우 `idempotencyKey` 원문 대신 `idempotencyKeyHash`를 저장한다.

---

## 2. MISSION 모듈

| API | 이벤트 타입 | 수집 목적 | 발생 시점 |
| --- | --- | --- | --- |
| `POST /api/mission/v1/missions/today-focus/next` | `MISSION_OFFERED` | 미션 제안 수, 첫 미션 진입률, 추천 카테고리/난이도 분포 분석 | 유저 미션 생성 트랜잭션 커밋 후 |
| `POST /api/mission/v1/missions/{missionId}/rejections` | `MISSION_REJECTED` | 미션 거절률, 회피 카테고리, 재추천 품질 분석 | 미션 `REJECTED` 전환 커밋 후 |
| `POST /api/mission/v1/missions/{missionId}/completion-sessions` | `MISSION_COMPLETION_SESSION_STARTED` | 완료 버튼 클릭 후 답변 제출 전 이탈률 분석 | 미션 `ANSWERING` 전환 및 완료 질문 row 생성 커밋 후 |
| `POST /api/mission/v1/missions/{missionId}/completion-answers` | `MISSION_COMPLETED` | 미션 완료율, 완료까지 걸린 시간, 보상 지급 후보량 분석 | 답변 저장 및 미션 `COMPLETED` 전환 커밋 후 |

미션 완료 이벤트 로그는 분석용 이벤트다.
별조각 지급의 재시도/멱등성은 `mission_outbox_events`의 `MISSION_REWARD_REQUESTED` 이벤트가 담당하며, event-log 저장 실패가 보상 지급 성공 여부를 바꾸지 않는다.

---

## 3. MISSION payload 예시

### 3.1 MISSION_OFFERED

```json
{
  "eventType": "MISSION_OFFERED",
  "sourceService": "mission",
  "userId": 1,
  "refType": "MISSION",
  "refId": 101,
  "properties": {
    "characterId": 3,
    "missionTemplateId": 12,
    "aiGenerationId": null,
    "missionDate": "2026-05-20",
    "stackOrder": 2,
    "category": "SPACE_RESET",
    "difficulty": "EASY",
    "rewardStarPiece": 10
  },
  "occurredAt": "2026-05-20T10:00:00+09:00"
}
```

### 3.2 MISSION_REJECTED

```json
{
  "eventType": "MISSION_REJECTED",
  "sourceService": "mission",
  "userId": 1,
  "refType": "MISSION",
  "refId": 101,
  "properties": {
    "characterId": 3,
    "missionTemplateId": 12,
    "aiGenerationId": null,
    "missionDate": "2026-05-20",
    "stackOrder": 2,
    "category": "SPACE_RESET",
    "difficulty": "EASY",
    "elapsedSecondsSinceOffered": 180
  },
  "occurredAt": "2026-05-20T10:03:00+09:00"
}
```

### 3.3 MISSION_COMPLETION_SESSION_STARTED

```json
{
  "eventType": "MISSION_COMPLETION_SESSION_STARTED",
  "sourceService": "mission",
  "userId": 1,
  "refType": "MISSION",
  "refId": 101,
  "properties": {
    "characterId": 3,
    "missionTemplateId": 12,
    "aiGenerationId": null,
    "missionDate": "2026-05-20",
    "stackOrder": 2,
    "category": "SPACE_RESET",
    "difficulty": "EASY",
    "questionId": 501,
    "inputType": "TEXT",
    "minLength": 1,
    "maxLength": 300,
    "elapsedSecondsSinceOffered": 420
  },
  "occurredAt": "2026-05-20T10:07:00+09:00"
}
```

### 3.4 MISSION_COMPLETED

```json
{
  "eventType": "MISSION_COMPLETED",
  "sourceService": "mission",
  "userId": 1,
  "refType": "MISSION",
  "refId": 101,
  "properties": {
    "characterId": 3,
    "missionTemplateId": 12,
    "aiGenerationId": null,
    "missionDate": "2026-05-20",
    "stackOrder": 2,
    "category": "SPACE_RESET",
    "difficulty": "EASY",
    "rewardStarPiece": 10,
    "answerLength": 24,
    "elapsedSecondsSinceOffered": 510,
    "elapsedSecondsSinceCompletionStarted": 90
  },
  "occurredAt": "2026-05-20T10:08:30+09:00"
}
```

`MISSION_COMPLETED`에는 사용자의 답변 전문을 넣지 않는다. 답변 전문은 `mission_completion_answers.answer_text`에만 저장하고, event-log에는 `answerLength`와 소요 시간처럼 분석용 요약값만 저장한다.

---

## 4. AI 모듈

| gRPC/API | 이벤트 타입 | 수집 목적 | 발생 시점 |
| --- | --- | --- | --- |
| `GenerateMissionTexts` | `AI_FALLBACK_USED` | fallback 비율, 문구 생성 품질, 정책 위반 유형 분석 | fallback 결과와 AI 사용 로그 저장 커밋 후 |
| `GenerateMissionTexts` | `AI_MISSION_GENERATION_FAILED` | fallback도 불가능한 실패 분석 | 후속 ADR에서 실패 저장 정책 결정 후 |

현재 MVP 구현에서는 외부 provider 실패, rate limit 초과, Redis rate limit 저장소 장애가 발생해도 사용자 흐름을 중단하지 않고 fallback 문구를 사용한다. 따라서 운영 모니터링의 1차 기준은 `AI_FALLBACK_USED` 이벤트와 `errorType`이다.

예를 들어 Redis rate limit 저장소 장애로 외부 provider 호출을 차단한 경우에는 문구 생성 결과를 fallback으로 저장하고, `AI_FALLBACK_USED`의 `errorType=RATE_LIMIT_UNAVAILABLE`, `usageStatus=RATE_LIMITED`로 추적한다. provider timeout이나 invalid output도 사용자에게 실패로 노출하기보다 fallback으로 흡수하고, 동일한 이벤트에서 원인을 구분한다.

`AI_MISSION_GENERATION_FAILED`는 fallback도 불가능한 실패를 별도로 저장하기로 결정했을 때 연결한다. 운영 알림 임계치, fail-open/fail-closed 정책, Redis 장애 시 알림 경로는 별도 ADR에서 결정한다.

---

## 5. AI payload 예시

### 5.1 AI_FALLBACK_USED

```json
{
  "eventType": "AI_FALLBACK_USED",
  "sourceService": "ai",
  "userId": 1,
  "refType": "AI_MISSION_GENERATION",
  "refId": 55,
  "properties": {
    "characterId": 3,
    "missionTemplateId": 12,
    "promptTemplateId": 4,
    "promptCategory": "CHARACTER_TONE",
    "provider": "LOCAL",
    "model": "local-tone-v1",
    "generationStatus": "FALLBACK",
    "usageStatus": "FALLBACK",
    "errorType": "POLICY_VIOLATION",
    "latencyMs": 42
  },
  "occurredAt": "2026-05-20T10:00:00+09:00"
}
```

### 5.2 AI_MISSION_GENERATION_FAILED

```json
{
  "eventType": "AI_MISSION_GENERATION_FAILED",
  "sourceService": "ai",
  "userId": 1,
  "refType": "AI_MISSION_GENERATION",
  "refId": 56,
  "properties": {
    "characterId": 3,
    "missionTemplateId": 12,
    "promptTemplateId": 4,
    "promptCategory": "CHARACTER_TONE",
    "provider": "GEMINI",
    "model": "gemini-2.5-flash",
    "generationStatus": "FAILED",
    "usageStatus": "FAILED",
    "errorType": "TIMEOUT",
    "latencyMs": 3000
  },
  "occurredAt": "2026-05-20T10:00:03+09:00"
}
```

AI 이벤트 로그에는 raw prompt와 raw response를 넣지 않는다. 상세 입력/출력은 `ai_mission_generations.request_context_json`, `ai_mission_generations.response_json`에서 추적하고, event-log에는 분석에 필요한 요약값만 저장한다.

`AI_FALLBACK_USED` 전송 실패는 AI 문구 생성 응답에 영향을 주지 않는다. event-log 모듈 장애는 애플리케이션 로그에 warn으로 남기고, 사용자는 mission template fallback 문구를 그대로 받는다.

---

## 6. 이벤트 타입 목록

```text
MISSION_OFFERED
MISSION_REJECTED
MISSION_COMPLETION_SESSION_STARTED
MISSION_COMPLETED
AI_FALLBACK_USED
AI_MISSION_GENERATION_FAILED (reserved)
```

---

## 7. 구현 메모

MISSION / AI 모듈은 user/item 모듈과 같은 이벤트 발행 방식을 따른다.

```text
ApplicationEventPublisher.publishEvent(...)
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
EventLogServiceGrpc.EventLogServiceBlockingStub.recordEventLog(...)
```

`properties` 객체는 gRPC 요청의 `propertiesJson` 문자열로 직렬화해 전달한다. 서버 내부 이벤트에서는 `contextJson`을 생략할 수 있다.

AI 이벤트 로그 테스트 기준은 다음과 같다.

```text
fallback 결과 저장 시 AI_FALLBACK_USED 이벤트를 발행한다.
성공 결과 저장 시 AI_FALLBACK_USED 이벤트를 발행하지 않는다.
event-log gRPC 전송 실패는 AI 문구 생성 흐름으로 전파하지 않는다.
propertiesJson에는 raw prompt, raw response, requestContextJson, responseJson을 넣지 않는다.
```

