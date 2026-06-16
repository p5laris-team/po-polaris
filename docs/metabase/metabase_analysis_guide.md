# Polaris Metabase 비즈니스/마케팅 분석 가이드

> 목적: Polaris 운영 로그를 단순히 DB에 쌓아 두는 것에서 끝내지 않고, Metabase로 DAU, 온보딩 전환율, 미션 funnel, 공유/상점 행동, 별조각 경제, AI fallback 품질을 비즈니스/마케팅 관점에서 분석할 수 있게 만든다.
>

> 작성 기준: 2026-06-09
>

---

## 🧭 0. 한 장 요약

Polaris는 이미 분석용 데이터를 쌓을 기반이 있다.

- `event-log` 모듈이 사용자 행동/도메인 이벤트를 `event_logs`에 저장한다.
- `event_logs`에는 `event_type`, `user_id`, `anonymous_id`, `properties_json`, `context_json`, `occurred_at`가 있다.
- `event-log` DB에는 `v_daily_user_activity`, `v_daily_mission_funnel`, `v_daily_share_store_activity` view가 있다.
- `user` DB에는 `v_daily_wallet_economy` view가 있다.
- `ai` DB에는 `v_daily_ai_quality` view가 있다.

따라서 Metabase는 다음 역할을 맡으면 좋다.

```
Grafana:
  운영/장애/성능 모니터링
  p95, error rate, DB/Redis, outbox backlog, AI fallback spike, Slack alert

Metabase:
  비즈니스/마케팅/제품 분석
  DAU, activation, mission funnel, retention, share conversion, reward economy, AI quality

ELK/Kibana:
  애플리케이션 로그 전문 검색
  지금 당장 필수는 아니고, traceId 기반 로그 검색이 커질 때 후보
```

포트폴리오 한 줄:

```
운영 이벤트 로그를 PostgreSQL event-log DB에 저장하고,
Metabase를 read-only 계정으로 연결해 DAU, 온보딩 전환율, 미션 funnel,
공유 전환, 별조각 경제, AI fallback 품질을 분석하는 비즈니스 대시보드를 설계했습니다.
```

---

## 🧩 1. Metabase는 어디에 붙는가

Metabase를 붙일 때는 DB를 두 종류로 나눠 생각해야 한다.

| 구분 | 의미 | 우리 프로젝트 기준 |
| --- | --- | --- |
| Metabase application DB | Metabase 자체 계정, 질문, 대시보드, 설정을 저장하는 DB | 운영에서는 별도 `metabase_app` PostgreSQL 권장 |
| 분석 대상 DB | Metabase가 읽어서 차트를 만드는 DB | `event_log`, `users`, `ai` 등 |

처음 local 실험은 간단히 갈 수 있다.

```
Metabase container
  -> postgres-compose:5432/event_log
  -> read-only user
  -> event_logs, v_daily_* views 조회
```

운영에서는 더 조심해야 한다.

```
Metabase
  -> private network
  -> read-only DB user
  -> 가능하면 read replica 또는 analytics DB
  -> event_logs 원본보다 view/materialized view 중심 조회
```

---

## 🗂️ 2. 현재 Polaris에서 쓸 수 있는 데이터 재료

### 2.1 event-log DB

파일 기준:

- `polaris/event-log/src/main/resources/db/migration/V1__init.sql`
- `polaris/event-log/src/main/resources/db/migration/V2__create_daily_aggregate_views.sql`
- `polaris/event-log/src/main/resources/db/migration/V3__create_daily_share_store_view.sql`
- `polaris/event-log/src/main/java/p5laris/eventlog/domain/application/EventLogService.java`

핵심 테이블:

```
event_logs
```

주요 컬럼:

| 컬럼 | 의미 | 분석 활용 |
| --- | --- | --- |
| `event_id` | 이벤트 UUID, 중복 저장 방지 | 멱등 저장 확인 |
| `event_type` | 이벤트 종류 | funnel, 추이, 행동 분석 |
| `source_service` | 이벤트 발생 모듈 | 서비스별 이벤트량 |
| `user_id` | 로그인 사용자 | DAU, 리텐션, 전환 |
| `anonymous_id` | 비로그인/공유 유입 사용자 | 공유/외부 유입 분석 후보 |
| `ref_type` | 참조 대상 타입 | MISSION, ITEM, SHARE 등 |
| `ref_id` | 참조 대상 ID | drill-down 후보 |
| `properties_json` | 이벤트 상세 속성 | 카테고리, 난이도, AI errorType, share platform |
| `context_json` | 화면/기기/버전 등 context | platform, appVersion, UTM 후보 |
| `occurred_at` | 실제 이벤트 발생 시각 | 시간대 분석 |
| `created_at` | DB 저장 시각 | ingestion delay 분석 |

현재 view:

| view | DB | 의미 |
| --- | --- | --- |
| `v_daily_user_activity` | `event_log` | DAU, 신규 가입, core action user |
| `v_daily_mission_funnel` | `event_log` | 온보딩, 캐릭터 생성, 미션 제안/거절/완료 |
| `v_daily_share_store_activity` | `event_log` | 공유 카드, 공유 완료, 아이템 구매 |

### 2.2 user DB

파일 기준:

- `polaris/user/src/main/resources/db/migration/V7__create_daily_wallet_economy_view.sql`
- `polaris/user/src/main/java/p5laris/user/domain/application/event/UserEventLogEvent.java`

현재 view:

| view | DB | 의미 |
| --- | --- | --- |
| `v_daily_wallet_economy` | `users` | 별조각 발행량, 소비량, 순증가량 |

이 view는 `star_piece_transactions` 기준이다.
즉 event-log가 아니라 실제 지갑 원장 기준이라 더 신뢰도가 높다.

### 2.3 ai DB

파일 기준:

- `polaris/ai/src/main/resources/db/migration/V5__create_daily_ai_quality_view.sql`
- `polaris/ai/src/main/java/p5laris/ai/domain/application/event/AiEventLogEvent.java`

현재 view:

| view | DB | 의미 |
| --- | --- | --- |
| `v_daily_ai_quality` | `ai` | AI 요청 수, fallback 수, rate limit 오류, 실패 수 |

AI 품질은 두 관점으로 볼 수 있다.

```
event-log DB:
  AI_FALLBACK_USED 이벤트로 사용자 흐름 안의 fallback 발생을 본다.

ai DB:
  ai_mission_generations와 v_daily_ai_quality로 provider/상태/실패율을 더 직접 본다.
```

### 2.4 character/share 데이터

파일 기준:

- `polaris/character/src/main/java/p5laris/character/domain/application/event/ShareEventLogEvent.java`
- `polaris/character/src/main/java/p5laris/character/domain/application/event/CharacterEventLogEvent.java`
- `polaris/character/src/main/java/p5laris/character/domain/application/ShareService.java`

이벤트 타입:

| 이벤트 | 의미 | 분석 활용 |
| --- | --- | --- |
| `CHARACTER_CREATED` | 캐릭터 생성 | 온보딩 이후 activation |
| `SHARE_CARD_CREATED` | 공유 카드 생성 | 바이럴 준비 행동 |
| `SHARE_COMPLETED` | 공유 시도 완료 | 공유 전환 |

현재 공유 링크 클릭은 `ShareService.recordShareClick`에서 로그로만 남기고 DB 영속화는 후속 확장으로 보인다.
따라서 Metabase 첫 단계에서는 공유 클릭보다 공유 카드/공유 완료/공유 보상 중심으로 본다.

### 2.5 item 데이터

파일 기준:

- `polaris/item/src/main/java/p5laris/item/domain/application/event/ItemEventLogEvent.java`

이벤트 타입:

| 이벤트 | 의미 | 분석 활용 |
| --- | --- | --- |
| `ITEM_PURCHASED` | 아이템 구매 | 구매율, 인기 아이템, 애착/소비 행동 |
| `STAR_PIECE_SPENT` | 별조각 소비 | 경제 시스템 작동 |

---

## 🛠️ 3. Local에서 Metabase 붙이는 방법

### 3.1 전제

현재 root `docker-compose.yml` 기준:

```
postgres container:
  name: postgres-compose
  image: pgvector/pgvector:pg16
  port: 5432
  network: spring-net
```

local backend script 기준 DB:

```
users
character
item
mission
ai
notification
event_log
```

### 3.2 Metabase 실행

가장 빠른 local 실험:

```bash
docker run -d \
  --name metabase \
  --network spring-net \
  -p 3000:3000 \
  metabase/metabase:latest
```

접속:

```
http://localhost:3000
```

공식 문서 기준으로 Metabase Docker quick start는 `metabase/metabase` 이미지를 `3000` 포트로 띄우는 흐름이다.

주의:

```
이 방식은 local 실험용이다.
Metabase container를 삭제하면 H2 기반 application data가 사라질 수 있다.
운영에서는 Metabase application DB를 PostgreSQL로 따로 둔다.
운영에서는 latest tag를 그대로 쓰지 말고, 검증한 Metabase 버전으로 고정한다.
```

### 3.3 운영형 Metabase 실행 예시

운영에서는 Metabase 자체 설정 저장용 DB를 따로 둔다.

```bash
docker run -d \
  --name metabase \
  --network spring-net \
  -p 3000:3000 \
  -e MB_DB_TYPE=postgres \
  -e MB_DB_DBNAME=metabase_app \
  -e MB_DB_PORT=5432 \
  -e MB_DB_USER=metabase_app_user \
  -e MB_DB_PASS='CHANGE_ME' \
  -e MB_DB_HOST=postgres-compose \
  metabase/metabase:v0.XX.X
```

여기서 `metabase_app`은 Metabase 자체 설정 저장용이다.
분석 대상인 `event_log`와 헷갈리면 안 된다.

`v0.XX.X`는 예시다.
운영에서는 팀이 실제로 검증한 버전 번호로 바꾼다.
버전을 고정해야 나중에 container를 재시작했을 때 갑자기 Metabase 동작이나 UI가 바뀌는 일을 줄일 수 있다.

---

## 🔐 4. DB read-only 계정 만들기

Metabase는 운영 DB에 절대 admin/root로 붙이지 않는다.

### 4.1 read-only role 생성

PostgreSQL 접속:

```bash
docker exec -it postgres-compose psql -U root -d postgres
```

role 생성:

```sql
CREATE ROLE metabase_readonly
  WITH LOGIN PASSWORD 'CHANGE_THIS_PASSWORD';

ALTER ROLE metabase_readonly SET default_transaction_read_only = on;
ALTER ROLE metabase_readonly SET statement_timeout = '10s';
```

### 4.2 event_log DB 권한

```sql
\c event_log

GRANT CONNECT ON DATABASE event_log TO metabase_readonly;
GRANT USAGE ON SCHEMA public TO metabase_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO metabase_readonly;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT ON TABLES TO metabase_readonly;
```

### 4.3 users DB 권한

별조각 경제 view를 Metabase에서 보려면 `users` DB도 read-only로 연결한다.

```sql
\c users

GRANT CONNECT ON DATABASE users TO metabase_readonly;
GRANT USAGE ON SCHEMA public TO metabase_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO metabase_readonly;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT ON TABLES TO metabase_readonly;
```

### 4.4 ai DB 권한

AI 품질 view를 Metabase에서 보려면 `ai` DB도 read-only로 연결한다.

```sql
\c ai

GRANT CONNECT ON DATABASE ai TO metabase_readonly;
GRANT USAGE ON SCHEMA public TO metabase_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO metabase_readonly;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT ON TABLES TO metabase_readonly;
```

### 4.5 더 안전한 운영 권한 원칙

운영에서는 가능하면 테이블 전체가 아니라 분석용 view만 노출한다.

```
권장:
  GRANT SELECT ON v_daily_user_activity TO metabase_readonly;
  GRANT SELECT ON v_daily_mission_funnel TO metabase_readonly;
  GRANT SELECT ON v_daily_share_store_activity TO metabase_readonly;

조심:
  event_logs 원본 전체 SELECT
```

이유:

- 원본 로그에는 예상 못 한 민감 값이 들어갈 수 있다.
- 무거운 원본 조회가 운영 DB에 부담을 줄 수 있다.
- view는 컬럼 이름과 의미를 대시보드 친화적으로 고정할 수 있다.

---

## 🔌 5. Metabase에서 DB 연결하기

Metabase UI:

```
Admin settings
-> Databases
-> Add a database
-> PostgreSQL
```

### 5.1 Polaris Event Log 연결

| 항목 | local 값 |
| --- | --- |
| Display name | `Polaris Event Log` |
| Host | `postgres-compose` |
| Port | `5432` |
| Database name | `event_log` |
| Username | `metabase_readonly` |
| Password | 위에서 만든 비밀번호 |
| SSL | local은 off, 운영은 on 검토 |
| Schemas | `public` |

중요:

```
Metabase container 안에서 localhost는 Metabase 자신이다.
Postgres container에 붙으려면 postgres-compose:5432를 사용한다.
```

### 5.2 Polaris User Analytics 연결

| 항목 | local 값 |
| --- | --- |
| Display name | `Polaris User Analytics` |
| Host | `postgres-compose` |
| Port | `5432` |
| Database name | `users` |
| Username | `metabase_readonly` |
| Password | 위에서 만든 비밀번호 |

### 5.3 Polaris AI Analytics 연결

| 항목 | local 값 |
| --- | --- |
| Display name | `Polaris AI Analytics` |
| Host | `postgres-compose` |
| Port | `5432` |
| Database name | `ai` |
| Username | `metabase_readonly` |
| Password | 위에서 만든 비밀번호 |

---

## 🗃️ 6. Metabase Collection 구조 추천

Metabase 안에서는 질문과 대시보드를 collection으로 정리한다.

```
📁 Polaris Analytics
  📁 00. Glossary & Models
  📁 01. Executive Overview
  📁 02. Growth & Activation
  📁 03. Mission Funnel
  📁 04. Share & Viral
  📁 05. Store & Reward Economy
  📁 06. AI Quality
  📁 07. Data Health
  📁 99. SQL Snippets
```

운영팀/발표용으로는 `01. Executive Overview`만 먼저 보여줘도 된다.
팀 내부 분석은 세부 dashboard로 들어가면 된다.

---

## 📈 7. Dashboard 1: Executive Overview

> 목적: 발표/데모 첫 화면. 서비스가 오늘 어떤 상태인지 한눈에 보여준다.
>

### 카드 구성

| 위치 | 카드 | 시각화 | 데이터 |
| --- | --- | --- | --- |
| 상단 KPI | DAU | Number | `v_daily_user_activity.dau` |
| 상단 KPI | 신규 가입 | Number | `new_users` |
| 상단 KPI | Core action users | Number | `core_action_users` |
| 상단 KPI | 미션 완료율 | Number | `MISSION_COMPLETED / MISSION_OFFERED` |
| 중단 | DAU 추이 | Line | `v_daily_user_activity` |
| 중단 | Activation funnel | Funnel/Bar | `v_daily_mission_funnel` |
| 중단 | 공유/상점 행동 | Line/Bar | `v_daily_share_store_activity` |
| 하단 | 별조각 경제 | Line | `v_daily_wallet_economy` |
| 하단 | AI fallback 품질 | Line | `v_daily_ai_quality` |

### SQL: DAU/신규/Core action

DB: `Polaris Event Log`

```sql
SELECT
  active_date,
  dau,
  new_users,
  core_action_users
FROM v_daily_user_activity
ORDER BY active_date DESC;
```

추천 시각화:

```
Line chart:
  x = active_date
  y = dau, new_users, core_action_users
```

### SQL: 오늘 요약 KPI

```sql
SELECT
  active_date,
  dau,
  new_users,
  core_action_users
FROM v_daily_user_activity
WHERE active_date = CURRENT_DATE;
```

데이터가 적으면 최근 날짜 기준으로 본다.

```sql
SELECT
  active_date,
  dau,
  new_users,
  core_action_users
FROM v_daily_user_activity
ORDER BY active_date DESC
LIMIT 1;
```

---

## 🚀 8. Dashboard 2: Growth & Activation

> 목적: 사용자가 처음 들어와 핵심 행동까지 가는지 본다.
>

### 핵심 질문

- 신규 가입자는 얼마나 들어오는가?
- 가입 후 온보딩을 완료하는가?
- 캐릭터를 생성하는가?
- 첫 미션 제안까지 도달하는가?
- 첫 미션 완료까지 가는가?

### SQL: 날짜별 activation funnel

DB: `Polaris Event Log`

```sql
SELECT
  active_date,
  onboarding_completed_events,
  character_created_events,
  mission_offered_events,
  mission_completed_events,
  ROUND(
    mission_completed_events::numeric
    / NULLIF(mission_offered_events, 0) * 100,
    2
  ) AS mission_completion_rate_percent
FROM v_daily_mission_funnel
ORDER BY active_date DESC;
```

추천 시각화:

```
Bar chart:
  x = active_date
  y = onboarding_completed_events, character_created_events, mission_offered_events, mission_completed_events
```

### SQL: 가입 -> 온보딩 완료 전환

```sql
WITH daily AS (
  SELECT
    DATE(occurred_at) AS active_date,
    COUNT(DISTINCT CASE WHEN event_type = 'USER_SIGNED_UP' THEN user_id END) AS signed_up_users,
    COUNT(DISTINCT CASE WHEN event_type = 'ONBOARDING_COMPLETED' THEN user_id END) AS onboarding_completed_users
  FROM event_logs
  WHERE event_type IN ('USER_SIGNED_UP', 'ONBOARDING_COMPLETED')
  GROUP BY DATE(occurred_at)
)
SELECT
  active_date,
  signed_up_users,
  onboarding_completed_users,
  ROUND(onboarding_completed_users::numeric / NULLIF(signed_up_users, 0) * 100, 2)
    AS signup_to_onboarding_rate_percent
FROM daily
ORDER BY active_date DESC;
```

### SQL: 온보딩 프로필 특성별 완료 분포

`UserEventLogEvent`는 onboarding metadata에 `missionIntensity`, `routineGoals`, `preferredTimeSlots`, `missionPlaceContexts`, `avoidedMissionTags`를 넣는다.

```sql
SELECT
  properties_json ->> 'missionIntensity' AS mission_intensity,
  COUNT(DISTINCT user_id) AS completed_users
FROM event_logs
WHERE event_type = 'ONBOARDING_COMPLETED'
GROUP BY properties_json ->> 'missionIntensity'
ORDER BY completed_users DESC;
```

활용:

```
가벼운 미션을 선호하는 사용자가 많은지,
강한 난이도를 원하는 사용자가 많은지,
마케팅 문구에서 어떤 톤을 써야 할지 참고한다.
```

### SQL: D1 재방문

```sql
WITH signup AS (
  SELECT
    user_id,
    MIN(DATE(occurred_at)) AS signup_date
  FROM event_logs
  WHERE event_type = 'USER_SIGNED_UP'
    AND user_id IS NOT NULL
  GROUP BY user_id
),
login AS (
  SELECT DISTINCT
    user_id,
    DATE(occurred_at) AS login_date
  FROM event_logs
  WHERE event_type = 'USER_LOGGED_IN'
    AND user_id IS NOT NULL
)
SELECT
  signup.signup_date,
  COUNT(*) AS signed_up_users,
  COUNT(login.user_id) AS d1_returned_users,
  ROUND(COUNT(login.user_id)::numeric / NULLIF(COUNT(*), 0) * 100, 2)
    AS d1_retention_percent
FROM signup
LEFT JOIN login
  ON login.user_id = signup.user_id
 AND login.login_date = signup.signup_date + INTERVAL '1 day'
GROUP BY signup.signup_date
ORDER BY signup.signup_date DESC;
```

주의:

```
현재 유저 수가 작으면 retention 수치는 크게 출렁인다.
35명 내외 데이터에서는 "경향 후보"로만 보고, 통계적으로 일반화하면 안 된다.
```

---

## 🧭 9. Dashboard 3: Mission Funnel & Personalization

> 목적: 미션 추천/거절/완료 흐름에서 어디가 막히는지 본다.
>

### 핵심 질문

- 제안된 미션이 완료까지 가는가?
- 어떤 카테고리의 거절률이 높은가?
- 완료 질문 세션 시작 후 답변 제출까지 이탈이 있는가?
- 난이도별 완료율은 어떤가?
- 완료까지 시간이 너무 긴 미션이 있는가?

### SQL: 미션 funnel 기본

```sql
SELECT
  active_date,
  mission_offered_events,
  mission_rejected_events,
  mission_completed_events,
  ROUND(mission_rejected_events::numeric / NULLIF(mission_offered_events, 0) * 100, 2)
    AS rejection_rate_percent,
  ROUND(mission_completed_events::numeric / NULLIF(mission_offered_events, 0) * 100, 2)
    AS completion_rate_percent
FROM v_daily_mission_funnel
ORDER BY active_date DESC;
```

### SQL: 카테고리별 미션 완료/거절률

`MissionEventLogEvent`는 `properties_json.category`, `difficulty`, `missionTemplateId`, `rewardStarPiece`, `elapsedSecondsSinceOffered`를 넣는다.

```sql
WITH by_category AS (
  SELECT
    properties_json ->> 'category' AS category,
    COUNT(*) FILTER (WHERE event_type = 'MISSION_OFFERED') AS offered,
    COUNT(*) FILTER (WHERE event_type = 'MISSION_REJECTED') AS rejected,
    COUNT(*) FILTER (WHERE event_type = 'MISSION_COMPLETED') AS completed
  FROM event_logs
  WHERE event_type IN ('MISSION_OFFERED', 'MISSION_REJECTED', 'MISSION_COMPLETED')
  GROUP BY properties_json ->> 'category'
)
SELECT
  category,
  offered,
  rejected,
  completed,
  ROUND(rejected::numeric / NULLIF(offered, 0) * 100, 2) AS rejection_rate_percent,
  ROUND(completed::numeric / NULLIF(offered, 0) * 100, 2) AS completion_rate_percent
FROM by_category
ORDER BY offered DESC;
```

비즈니스 해석:

```
거절률이 높은 category:
  추천 적합도가 낮거나 미션 부담이 클 수 있다.

완료율이 높은 category:
  마케팅/온보딩에서 강조할 수 있는 핵심 가치 후보.
```

### SQL: 난이도별 완료율

```sql
WITH by_difficulty AS (
  SELECT
    properties_json ->> 'difficulty' AS difficulty,
    COUNT(*) FILTER (WHERE event_type = 'MISSION_OFFERED') AS offered,
    COUNT(*) FILTER (WHERE event_type = 'MISSION_COMPLETED') AS completed
  FROM event_logs
  WHERE event_type IN ('MISSION_OFFERED', 'MISSION_COMPLETED')
  GROUP BY properties_json ->> 'difficulty'
)
SELECT
  difficulty,
  offered,
  completed,
  ROUND(completed::numeric / NULLIF(offered, 0) * 100, 2) AS completion_rate_percent
FROM by_difficulty
ORDER BY difficulty;
```

### SQL: 완료 질문 이탈률

```sql
WITH daily AS (
  SELECT
    DATE(occurred_at) AS active_date,
    COUNT(*) FILTER (WHERE event_type = 'MISSION_COMPLETION_SESSION_STARTED') AS completion_sessions,
    COUNT(*) FILTER (WHERE event_type = 'MISSION_COMPLETED') AS mission_completed
  FROM event_logs
  WHERE event_type IN ('MISSION_COMPLETION_SESSION_STARTED', 'MISSION_COMPLETED')
  GROUP BY DATE(occurred_at)
)
SELECT
  active_date,
  completion_sessions,
  mission_completed,
  ROUND(mission_completed::numeric / NULLIF(completion_sessions, 0) * 100, 2)
    AS answer_submit_rate_percent
FROM daily
ORDER BY active_date DESC;
```

해석:

```
완료 버튼은 눌렀는데 답변 제출이 적다면,
완료 질문 UI가 부담스럽거나 질문 문구가 어렵거나 입력 길이 제한이 불편할 수 있다.
```

### SQL: 완료까지 걸린 시간

```sql
SELECT
  properties_json ->> 'category' AS category,
  properties_json ->> 'difficulty' AS difficulty,
  AVG((properties_json ->> 'elapsedSecondsSinceOffered')::numeric) AS avg_seconds_since_offered,
  PERCENTILE_CONT(0.5) WITHIN GROUP (
    ORDER BY (properties_json ->> 'elapsedSecondsSinceOffered')::numeric
  ) AS median_seconds_since_offered
FROM event_logs
WHERE event_type = 'MISSION_COMPLETED'
  AND properties_json ? 'elapsedSecondsSinceOffered'
GROUP BY
  properties_json ->> 'category',
  properties_json ->> 'difficulty'
ORDER BY avg_seconds_since_offered DESC;
```

---

## 📨 10. Dashboard 4: Share & Viral

> 목적: 공유 기능이 실제 확산/재방문/가입 후보로 이어질 수 있는지 본다.
>

### 현재 가능한 분석

현재 코드 기준:

- `SHARE_CARD_CREATED`: 공유 카드 생성
- `SHARE_COMPLETED`: 공유 시도 완료
- 공유 보상 획득 여부: `SHARE_COMPLETED.properties_json.rewardEarned = true` 기준
- `share-clicks`: 현재는 로그로만 남기고 DB 영속화는 후속 확장 후보

따라서 1차 Metabase dashboard는 아래 정도로 시작한다.

### SQL: 공유/상점 daily view

```sql
SELECT
  active_date,
  share_card_created_events,
  share_completed_events,
  item_purchased_events,
  unique_buyers
FROM v_daily_share_store_activity
ORDER BY active_date DESC;
```

### SQL: 공유 보상 획득 daily 흐름

현재 코드의 `ShareEventLogEvent.shareCompleted`는 `SHARE_COMPLETED` 이벤트 properties에 `platform`, `rewardEarned`를 넣는다.
따라서 Metabase의 공유 보상 지표는 별도 `SHARE_REWARD_CLAIMED` 이벤트가 아니라 `rewardEarned=true`를 기준으로 본다.

```sql
WITH reward_daily AS (
  SELECT
    DATE(occurred_at) AS active_date,
    COUNT(*) FILTER (
      WHERE properties_json ->> 'rewardEarned' = 'true'
    ) AS reward_earned_events
  FROM event_logs
  WHERE event_type = 'SHARE_COMPLETED'
  GROUP BY DATE(occurred_at)
)
SELECT
  share_store.active_date,
  share_store.share_card_created_events,
  share_store.share_completed_events,
  COALESCE(reward_daily.reward_earned_events, 0) AS reward_earned_events,
  share_store.item_purchased_events,
  share_store.unique_buyers
FROM v_daily_share_store_activity share_store
LEFT JOIN reward_daily
  ON reward_daily.active_date = share_store.active_date
ORDER BY share_store.active_date DESC;
```

### SQL: 공유 카드 생성 -> 공유 완료 전환

```sql
SELECT
  active_date,
  share_card_created_events,
  share_completed_events,
  ROUND(
    share_completed_events::numeric
    / NULLIF(share_card_created_events, 0) * 100,
    2
  ) AS share_completion_rate_percent
FROM v_daily_share_store_activity
ORDER BY active_date DESC;
```

### SQL: platform별 공유 완료

`ShareEventLogEvent.shareCompleted`는 `platform`, `rewardEarned`를 properties에 넣는다.

```sql
SELECT
  properties_json ->> 'platform' AS platform,
  COUNT(*) AS share_completed_events,
  COUNT(DISTINCT user_id) AS share_users,
  COUNT(*) FILTER (
    WHERE properties_json ->> 'rewardEarned' = 'true'
  ) AS reward_earned_events
FROM event_logs
WHERE event_type = 'SHARE_COMPLETED'
GROUP BY properties_json ->> 'platform'
ORDER BY share_completed_events DESC;
```

마케팅 해석:

```
특정 platform에서 공유 시도가 많다면,
해당 채널에 맞춘 카피/이미지/랜딩 전략을 세울 수 있다.
```

### 후속 개선 후보: share-clicks 영속화

현재 `ShareService.recordShareClick`은 클릭을 `log.info`로 기록한다.
마케팅 분석까지 하려면 DB 이벤트로도 남기는 것이 좋다.

후속 이벤트 후보:

```
SHARE_LINK_CLICKED
```

properties 후보:

```json
{
  "shareId": "sh_abc123",
  "referrer": "...",
  "utmSource": "x",
  "utmMedium": "social",
  "utmCampaign": "character_card"
}
```

이 이벤트가 생기면 Metabase에서 아래 funnel이 가능해진다.

```
SHARE_CARD_CREATED
-> SHARE_COMPLETED
-> SHARE_LINK_CLICKED
-> USER_SIGNED_UP
-> ONBOARDING_COMPLETED
-> MISSION_COMPLETED
```

---

## 🛒 11. Dashboard 5: Store & Reward Economy

> 목적: 별조각이 발행되고 소비되는 흐름이 건강한지, 아이템 구매가 애착 행동으로 이어지는지 본다.
>

### SQL: 별조각 경제 daily view

DB: `Polaris User Analytics`

```sql
SELECT
  active_date,
  total_issued,
  total_consumed,
  net_increase
FROM v_daily_wallet_economy
ORDER BY active_date DESC;
```

시각화:

```
Line chart:
  total_issued
  total_consumed
  net_increase
```

해석:

```
total_issued만 계속 늘고 total_consumed가 낮다:
  별조각을 쓸 이유가 약하다.

total_consumed가 너무 높다:
  사용자 잔액 부족/구매 실패가 늘 수 있다.

net_increase가 계속 크다:
  경제 시스템에 inflation이 생긴다.
```

### SQL: 아이템 구매 이벤트

DB: `Polaris Event Log`

```sql
SELECT
  DATE(occurred_at) AS active_date,
  COUNT(*) AS item_purchased_events,
  COUNT(DISTINCT user_id) AS unique_buyers,
  SUM((properties_json ->> 'totalPrice')::numeric) AS total_purchase_amount
FROM event_logs
WHERE event_type = 'ITEM_PURCHASED'
GROUP BY DATE(occurred_at)
ORDER BY active_date DESC;
```

### SQL: 인기 아이템

```sql
SELECT
  properties_json ->> 'itemName' AS item_name,
  properties_json ->> 'itemType' AS item_type,
  COUNT(*) AS purchase_count,
  COUNT(DISTINCT user_id) AS unique_buyers,
  SUM((properties_json ->> 'totalPrice')::numeric) AS total_spent
FROM event_logs
WHERE event_type = 'ITEM_PURCHASED'
GROUP BY
  properties_json ->> 'itemName',
  properties_json ->> 'itemType'
ORDER BY purchase_count DESC;
```

### SQL: 구매 유저의 미션 완료 행동

```sql
WITH buyers AS (
  SELECT DISTINCT user_id
  FROM event_logs
  WHERE event_type = 'ITEM_PURCHASED'
    AND user_id IS NOT NULL
),
mission_users AS (
  SELECT
    user_id,
    COUNT(*) AS completed_count
  FROM event_logs
  WHERE event_type = 'MISSION_COMPLETED'
    AND user_id IS NOT NULL
  GROUP BY user_id
)
SELECT
  CASE WHEN buyers.user_id IS NULL THEN 'non_buyer' ELSE 'buyer' END AS user_segment,
  COUNT(DISTINCT mission_users.user_id) AS users,
  AVG(mission_users.completed_count) AS avg_completed_missions
FROM mission_users
LEFT JOIN buyers
  ON buyers.user_id = mission_users.user_id
GROUP BY CASE WHEN buyers.user_id IS NULL THEN 'non_buyer' ELSE 'buyer' END;
```

주의:

```
이 쿼리는 "구매해서 미션을 더 많이 했다"는 인과를 증명하지 않는다.
단지 구매 유저와 비구매 유저의 행동 차이를 관찰하는 출발점이다.
```

---

## 🤖 12. Dashboard 6: AI Quality & Personalization

> 목적: AI가 사용자 경험을 망치지 않고 있는지, fallback이 어느 정도 발생하는지, 어떤 원인이 많은지 본다.
>

### SQL: AI daily quality view

DB: `Polaris AI Analytics`

```sql
SELECT
  active_date,
  total_ai_requests,
  fallback_requests,
  rate_limit_errors,
  failed_requests,
  ROUND(fallback_requests::numeric / NULLIF(total_ai_requests, 0) * 100, 2)
    AS fallback_rate_percent,
  ROUND(failed_requests::numeric / NULLIF(total_ai_requests, 0) * 100, 2)
    AS failure_rate_percent
FROM v_daily_ai_quality
ORDER BY active_date DESC;
```

### SQL: event-log 기준 AI fallback 원인

DB: `Polaris Event Log`

```sql
SELECT
  DATE(occurred_at) AS active_date,
  properties_json ->> 'errorType' AS error_type,
  properties_json ->> 'provider' AS provider,
  COUNT(*) AS fallback_events,
  AVG((properties_json ->> 'latencyMs')::numeric) AS avg_latency_ms
FROM event_logs
WHERE event_type = 'AI_FALLBACK_USED'
GROUP BY
  DATE(occurred_at),
  properties_json ->> 'errorType',
  properties_json ->> 'provider'
ORDER BY active_date DESC, fallback_events DESC;
```

### SQL: AI fallback 이후 미션 완료율 후보

```sql
WITH fallback_users AS (
  SELECT DISTINCT user_id
  FROM event_logs
  WHERE event_type = 'AI_FALLBACK_USED'
    AND user_id IS NOT NULL
),
mission_completed AS (
  SELECT
    user_id,
    COUNT(*) AS completed_count
  FROM event_logs
  WHERE event_type = 'MISSION_COMPLETED'
    AND user_id IS NOT NULL
  GROUP BY user_id
)
SELECT
  CASE WHEN fallback_users.user_id IS NULL THEN 'no_fallback' ELSE 'fallback_experienced' END AS segment,
  COUNT(DISTINCT mission_completed.user_id) AS users,
  AVG(mission_completed.completed_count) AS avg_completed_missions
FROM mission_completed
LEFT JOIN fallback_users
  ON fallback_users.user_id = mission_completed.user_id
GROUP BY CASE WHEN fallback_users.user_id IS NULL THEN 'no_fallback' ELSE 'fallback_experienced' END;
```

주의:

```
이 쿼리는 AI fallback이 미션 완료율을 떨어뜨렸다는 인과를 증명하지 않는다.
fallback 경험자와 비경험자의 행동 차이를 보는 탐색 지표다.
```

---

## 🩺 13. Dashboard 7: Data Health

> 목적: 분석 데이터 자체가 믿을 수 있는지 본다.
>

비즈니스 대시보드는 데이터가 안 들어오면 조용히 틀린 결론을 낼 수 있다.
그래서 데이터 건강 dashboard가 필요하다.

### SQL: event-log 수집 지연

```sql
SELECT
  DATE(created_at) AS created_date,
  COUNT(*) AS event_count,
  AVG(EXTRACT(EPOCH FROM (created_at - occurred_at))) AS avg_ingestion_delay_seconds,
  PERCENTILE_CONT(0.95) WITHIN GROUP (
    ORDER BY EXTRACT(EPOCH FROM (created_at - occurred_at))
  ) AS p95_ingestion_delay_seconds
FROM event_logs
GROUP BY DATE(created_at)
ORDER BY created_date DESC;
```

### SQL: 이벤트 타입별 최근 수집량

```sql
SELECT
  event_type,
  source_service,
  COUNT(*) AS events_24h,
  MAX(created_at) AS latest_created_at
FROM event_logs
WHERE created_at >= now() - INTERVAL '24 hours'
GROUP BY event_type, source_service
ORDER BY events_24h DESC;
```

### SQL: 중복 event_id 확인

`event_id`에는 unique 제약이 있으므로 실제 중복 row는 없어야 한다.
그래도 데이터 건강 카드로 남겨 둘 수 있다.

```sql
SELECT
  event_id,
  COUNT(*) AS count
FROM event_logs
GROUP BY event_id
HAVING COUNT(*) > 1;
```

### SQL: user_id가 없는 이벤트 비율

```sql
SELECT
  event_type,
  COUNT(*) AS total_events,
  COUNT(*) FILTER (WHERE user_id IS NULL) AS null_user_events,
  ROUND(
    COUNT(*) FILTER (WHERE user_id IS NULL)::numeric
    / NULLIF(COUNT(*), 0) * 100,
    2
  ) AS null_user_rate_percent
FROM event_logs
GROUP BY event_type
ORDER BY null_user_rate_percent DESC;
```

해석:

```
공유 링크 클릭 같은 public 이벤트는 user_id가 없을 수 있다.
하지만 로그인 후 발생해야 하는 이벤트에서 user_id가 비어 있으면 수집 버그일 수 있다.
```

---

## 🧱 14. Dashboard filter 추천

Metabase dashboard에는 filter를 걸어야 보기 편하다.

| 필터 | 연결 대상 |
| --- | --- |
| Date range | `active_date`, `occurred_at` |
| Event type | `event_type` |
| Source service | `source_service` |
| Mission category | `properties_json ->> 'category'` |
| Difficulty | `properties_json ->> 'difficulty'` |
| Platform | `properties_json ->> 'platform'`, `context_json ->> 'platform'` |
| AI errorType | `properties_json ->> 'errorType'` |
| Provider | `properties_json ->> 'provider'` |

Notion/발표용 dashboard 구성:

```
상단:
  핵심 KPI 4개

중단:
  날짜별 추이 line chart
  funnel bar chart

하단:
  원인 breakdown table
  다음 액션 메모
```

---

## 🧾 15. 이벤트 사전

현재 코드/문서에서 확인한 주요 이벤트 타입이다.

| 이벤트 | 발생 모듈 | 의미 | 분석 질문 |
| --- | --- | --- | --- |
| `USER_SIGNED_UP` | user | 신규 가입 | 가입 추이, 유입 성과 |
| `USER_LOGGED_IN` | user | 로그인 | DAU, retention |
| `ONBOARDING_PROFILE_SAVED` | user | 온보딩 중간 저장 | 이탈 구간 |
| `ONBOARDING_COMPLETED` | user | 온보딩 완료 | activation |
| `ATTENDANCE_CHECKED` | user | 출석 | 재방문 습관 |
| `STAR_PIECE_EARNED` | user | 별조각 획득 | 보상 발행 |
| `CHARACTER_CREATED` | character | 캐릭터 생성 | activation |
| `SHARE_CARD_CREATED` | character | 공유 카드 생성 | 공유 준비 |
| `SHARE_COMPLETED` | character | 공유 시도 | viral 행동 |
| `MISSION_OFFERED` | mission | 미션 제안 | 추천 노출 |
| `MISSION_REJECTED` | mission | 미션 거절 | 추천 적합도 |
| `MISSION_COMPLETION_SESSION_STARTED` | mission | 완료 질문 시작 | 완료 흐름 진입 |
| `MISSION_COMPLETED` | mission | 미션 완료 | 핵심 행동 |
| `ITEM_PURCHASED` | item | 아이템 구매 | 소비/애착 |
| `STAR_PIECE_SPENT` | item | 별조각 소비 | 경제 시스템 |
| `AI_FALLBACK_USED` | ai | AI fallback 사용 | AI 품질/장애 |
| `AI_MISSION_GENERATION_FAILED` | ai | AI 생성 실패 후보 | 후속 확장 |

---

## 🔒 16. 개인정보/민감정보 정책

Metabase는 내부 사용자에게 데이터를 보여주는 도구다.
그래서 로그에 무엇을 넣고 무엇을 보여주지 않을지 더 중요하다.

현재 문서 기준으로 event-log에 넣지 말아야 하는 값:

- 사용자 완료 답변 전문
- AI raw prompt
- AI raw response
- access token
- refresh token
- OAuth code
- idempotencyKey 원문
- 대용량 JSON 전문

Metabase dashboard에서도 조심할 것:

| 위험 | 대응 |
| --- | --- |
| `user_id`를 그대로 노출 | 집계 dashboard에서는 distinct count만 표시 |
| 답변 전문 노출 | event-log에는 `answerLength`만 사용 |
| AI prompt/response 노출 | AI quality에는 status/errorType/provider만 사용 |
| idempotency key 원문 노출 | hash 또는 count만 사용 |
| public share URL 대량 노출 | 필요 시 샘플/집계만 사용 |

---

## 🚨 17. Grafana와 Metabase 역할 분리

Metabase dashboard를 만들면 Grafana가 필요 없어지는 것이 아니다.
둘은 목적이 다르다.

| 질문 | 도구 |
| --- | --- |
| 지금 API p95가 튀는가? | Grafana |
| outbox backlog가 계속 쌓이는가? | Grafana |
| AI fallback이 장애 수준으로 급증했는가? | Grafana |
| 온보딩 완료율은 좋아졌는가? | Metabase |
| 미션 카테고리별 완료율은 어떤가? | Metabase |
| 아이템 구매 유저가 더 오래 남는가? | Metabase |
| 공유가 가입으로 이어지는가? | Metabase |

포트폴리오 문장:

```
Grafana는 운영 이상 감지와 알림,
Metabase는 사용자 행동 기반 제품/마케팅 분석으로 역할을 분리했습니다.
같은 event-log 데이터라도 Grafana에서는 급감/급증을 alert로 보고,
Metabase에서는 funnel, retention, cohort, reward economy를 의사결정 지표로 분석했습니다.
```

---

## 🧪 18. 초기 구축 순서

### 1단계: local Metabase 띄우기

- [ ]  `metabase/metabase:latest` Docker 실행
- [ ]  `http://localhost:3000` 접속
- [ ]  admin 계정 생성
- [ ]  `Polaris Event Log` DB 연결

### 2단계: read-only 검증

- [ ]  `metabase_readonly` 계정 생성
- [ ]  `SELECT` 가능 확인
- [ ]  `INSERT/UPDATE/DELETE` 실패 확인
- [ ]  운영 DB에는 admin/root 계정으로 연결하지 않기

### 3단계: 기본 dashboard 3개

- [ ]  `Executive Overview`
- [ ]  `Growth & Activation`
- [ ]  `Mission Funnel`

### 4단계: 확장 dashboard

- [ ]  `Share & Viral`
- [ ]  `Store & Reward Economy`
- [ ]  `AI Quality`
- [ ]  `Data Health`

### 5단계: 발표/포폴 캡처

- [ ]  dashboard 첫 화면 캡처
- [ ]  funnel chart 캡처
- [ ]  별조각 경제 chart 캡처
- [ ]  AI fallback chart 캡처
- [ ]  read-only 권한 설계 캡처 또는 설명

---

## 🧠 19. 발표 스크립트

짧은 버전:

```
저희는 운영 로그를 단순 저장에 그치지 않고 Metabase 대시보드로 연결했습니다.
event-log DB에는 가입, 로그인, 온보딩, 미션 제안/거절/완료, 공유, 아이템 구매, AI fallback 이벤트가 쌓이고,
이를 기반으로 DAU, activation, mission funnel, reward economy, AI quality를 분석했습니다.
운영 장애 지표는 Grafana에서 보고, 제품/마케팅 지표는 Metabase에서 보도록 역할을 분리했습니다.
```

조금 더 기술적인 버전:

```
Metabase는 운영 DB에 직접 admin으로 붙이지 않고 read-only 계정으로 연결했습니다.
원본 event_logs를 무겁게 조회하기보다 daily aggregate view를 우선 사용했고,
민감한 답변 전문이나 AI raw prompt는 event-log에 저장하지 않는 정책을 유지했습니다.
이를 통해 운영 DB를 보호하면서도 비즈니스/마케팅 분석에 필요한 funnel과 retention 지표를 볼 수 있게 했습니다.
```

면접에서 좋은 한 문장:

```
저희 프로젝트의 차별점은 로그를 쌓았다는 것보다, 그 로그를 제품 개선 질문으로 바꿔 대시보드화했다는 점입니다.
```

---

## ❓ 20. 예상 질문과 답변

### Q1. 왜 Grafana가 아니라 Metabase인가요?

Grafana는 운영 metric과 alert에 강하고, Metabase는 DB 기반 비즈니스 분석에 강합니다. 저희는 API p95, outbox backlog, AI fallback spike 같은 운영 이상은 Grafana로 보고, DAU, 온보딩 전환율, 미션 funnel, 별조각 경제 같은 제품 지표는 Metabase로 보도록 역할을 분리했습니다.

### Q2. Metabase를 운영 DB에 붙이면 위험하지 않나요?

위험할 수 있습니다. 그래서 admin 계정이 아니라 read-only 계정을 만들고, 가능하면 read replica나 analytics DB에 붙이는 것이 좋습니다. 또한 원본 테이블보다 view/materialized view 중심으로 조회해 운영 DB 부담을 줄입니다.

### Q3. event-log가 실패하면 서비스도 실패하나요?

아닙니다. 현재 설계는 핵심 트랜잭션 commit 후 비동기로 event-log를 기록하며, event-log 장애가 로그인, 미션 완료, 아이템 구매 같은 핵심 흐름을 실패로 전파하지 않도록 되어 있습니다. event-log는 분석/감사 데이터이고, 보상 원장은 각 도메인 테이블과 outbox가 담당합니다.

### Q4. Metabase로 진짜 마케팅 분석도 가능한가요?

가능합니다. 현재는 가입, 온보딩, 미션 완료, 공유, 아이템 구매 이벤트를 기반으로 기본 funnel과 행동 분석을 할 수 있습니다. 더 정교한 campaign 분석을 하려면 `context_json`이나 share click 이벤트에 `utmSource`, `utmMedium`, `utmCampaign`을 안정적으로 저장해야 합니다.

### Q5. 데이터가 적어도 의미가 있나요?

유저가 35명 정도라면 통계적으로 큰 결론을 내리기는 어렵습니다. 하지만 dashboard 구조, 이벤트 설계, funnel 정의, 데이터 품질 검증 체계를 보여주는 데는 충분합니다. 포트폴리오에서는 “정확한 시장 예측”이 아니라 “운영 이후 데이터를 보고 개선할 수 있는 구조”를 강조해야 합니다.

### Q6. PostHog랑 뭐가 다른가요?

PostHog는 프론트 사용자 행동, funnel, cohort, feature flag, A/B test에 강한 product analytics 도구입니다. Metabase는 이미 DB에 쌓인 데이터와 SQL/view 기반 BI 분석에 강합니다. 지금 Polaris는 서버 event-log DB가 이미 있으므로 Metabase가 먼저입니다. 이후 프론트 클릭/경로 분석이 필요해지면 PostHog를 검토할 수 있습니다.

### Q7. ELK/Kibana는 안 쓰나요?

ELK/Kibana는 애플리케이션 로그 전문 검색과 장애 원인 추적에 더 가깝습니다. 지금 비즈니스/마케팅 분석은 Metabase가 더 자연스럽습니다. 서버 로그가 많아져 traceId 기반 검색이 필요해지면 ELK를 후속 후보로 둘 수 있습니다.

---

## 🧭 21. 후속 고도화 로드맵

### 가까운 고도화

- [ ]  `v_hourly_event_counts` view 추가
- [ ]  `v_event_log_ingestion_health` view 추가
- [ ]  `SHARE_LINK_CLICKED` 이벤트 DB 저장
- [ ]  `context_json`에 `platform`, `appVersion`, `utmSource`, `utmMedium`, `utmCampaign` 정리
- [ ]  Metabase dashboard date filter 통일
- [ ]  핵심 dashboard를 PDF/Slack subscription으로 공유

### 중간 고도화

- [ ]  materialized view 도입
- [ ]  read replica 또는 analytics DB 분리
- [ ]  n8n daily business report 자동화
- [ ]  Metabase 질문/대시보드 naming convention
- [ ]  dashboard별 owner 지정

### 큰 고도화

- [ ]  Kafka/CDC로 analytics DB 적재
- [ ]  ClickHouse/BigQuery 같은 OLAP 후보 검토
- [ ]  PostHog로 프론트 행동/코호트/feature flag 확장
- [ ]  개인정보 consent/retention 정책 강화

---

## 📚 22. 공식 자료

- Metabase Docker 실행 문서
    - local quick start, production application DB 분리, Docker 실행 방식을 확인한다.
- Metabase PostgreSQL 연결 문서
    - PostgreSQL 연결 시 display name, host, port, database name 등 입력값을 확인한다.
- Metabase DB 사용자/권한 문서
    - 분석용 DB에는 전용 read-only user와 최소 권한을 권장한다.
- Metabase Dashboard 문서
    - dashboard는 여러 question/card를 묶은 공유 가능한 report이며, filter와 subscription을 구성할 수 있다.
- Metabase SQL editor 문서
    - SQL/native query로 직접 질문을 만들 때 참고한다.
- PostgreSQL JSON Functions and Operators
    - `properties_json ->> 'category'` 같은 JSONB 필드 추출 문법을 확인한다.

---

## ✅ 23. 최종 체크리스트

- [ ]  Metabase를 admin DB 계정으로 연결하지 않았다.
- [ ]  `event_log` DB에 read-only 계정으로 연결했다.
- [ ]  `users`, `ai` DB는 필요한 view만 read-only로 연결했다.
- [ ]  원본 event_logs 조회보다 daily view를 우선 사용했다.
- [ ]  dashboard는 한 화면에 너무 많은 카드를 넣지 않았다.
- [ ]  date filter를 모든 주요 card에 연결했다.
- [ ]  `user_id` raw list를 대시보드에 노출하지 않았다.
- [ ]  완료 답변 전문, AI raw prompt/response를 노출하지 않았다.
- [ ]  Grafana와 Metabase의 역할을 분리해서 설명할 수 있다.
- [ ]  포트폴리오 캡처용 dashboard가 준비됐다.

---

## 🏁 24. 결론

Polaris에서 Metabase는 “기술을 하나 더 붙였다”가 아니라 “운영 로그를 제품 개선 질문으로 바꿨다”는 증거가 된다.

가장 중요한 흐름:

```
event-log 수집
-> daily aggregate view
-> Metabase dashboard
-> DAU / activation / mission funnel / reward economy / AI quality 분석
-> 제품 개선과 마케팅 의사결정
```

이렇게 말하면 좋다.
```
저희는 단순히 로그를 많이 쌓은 것이 아니라,
그 로그를 Metabase 대시보드로 연결해 사용자가 어디서 이탈하는지,
어떤 미션이 잘 완료되는지,
공유와 아이템 구매가 실제 행동으로 이어지는지,
AI fallback이 사용자 경험에 어떤 영향을 줄 수 있는지를 볼 수 있게 설계했습니다.
```
