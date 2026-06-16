# 📊 Polaris Metabase JSON Import 가이드

> 목적: `METABASE_POLARIS_DASHBOARD_IMPORT.json`을 Metabase에 바로 넣어서 Polaris 비즈니스/마케팅 분석 대시보드를 만든다.

---

## 1. 먼저 중요한 사실

Metabase에는 두 종류의 import가 있다.

| 방식 | 무료 self-host 가능? | 설명 |
| --- | --- | --- |
| 공식 serialization import | Pro/Enterprise | Metabase가 export한 YAML/tgz를 다시 import하는 방식 |
| API 기반 JSON import | 가능 | JSON 템플릿을 읽어서 API로 collection, question, dashboard를 생성하는 방식 |

이 폴더에 만든 방식은 두 번째다.

```text
JSON 템플릿
-> Node import script
-> Metabase HTTP API
-> Collection / Question / Dashboard 자동 생성
```

즉, Metabase UI에서 JSON 파일을 업로드하는 방식은 아니고, script가 JSON을 읽어서 Metabase API에 넣어 주는 방식이다.

---

## 2. 생성된 파일

| 파일 | 역할 |
| --- | --- |
| `METABASE_POLARIS_DASHBOARD_IMPORT.json` | 대시보드/카드/SQL/layout 템플릿 |
| `import-metabase-polaris-dashboard.mjs` | JSON을 읽어서 Metabase API로 import하는 Node script |
| `METABASE_DASHBOARD_JSON_IMPORT_GUIDE.md` | 지금 보고 있는 실행 가이드 |

---

## 3. Metabase가 떠 있어야 한다

local 예시:

```bash
docker run -d \
  --name metabase \
  --network spring-net \
  -p 3000:3000 \
  metabase/metabase:latest
```

접속:

```text
http://localhost:3000
```

운영에서는 `latest`를 쓰지 말고 검증한 버전으로 고정한다.

---

## 4. Metabase에 DB 연결을 먼저 해 둔다

Metabase Admin > Databases에서 아래 DB를 연결한다.

| Metabase 표시 이름 추천 | 실제 DB | 필요 이유 |
| --- | --- | --- |
| `Polaris Event Log` | `event_log` | event_logs, daily user/mission/share view |
| `Polaris User Analytics` | `users` | 별조각 경제 view |
| `Polaris AI Analytics` | `ai` | AI 품질 view |

script는 DB 이름이 위와 같으면 자동으로 database id를 찾는다.
이름이 다르면 환경변수로 직접 넣으면 된다.

### 4.1 DB/view 준비 체크

Metabase import는 Polaris Spring 서버를 직접 호출하지 않고, Metabase에 연결된 DB와 view를 대상으로 SQL 카드를 만든다.
따라서 import 전에 아래 DB schema/view가 준비되어 있어야 한다.

| DB | 필요한 테이블/view | 정의 위치 |
| --- | --- | --- |
| `event_log` | `event_logs`, `v_daily_user_activity`, `v_daily_mission_funnel`, `v_daily_share_store_activity` | `event-log/src/main/resources/db/migration/V1__init.sql` |
| `users` | `star_piece_transactions`, `v_daily_wallet_economy` | `user/src/main/resources/db/migration/V1__init.sql` |
| `ai` | `ai_mission_generations`, `ai_usage_logs`, `v_daily_ai_quality` | `ai/src/main/resources/db/migration/V1__init.sql` |

DB가 비어 있어도 import는 가능하다. 이 경우 dashboard card는 `No results`로 보일 수 있다.
반대로 `relation does not exist`가 뜨면 Metabase가 바라보는 DB에 위 table/view가 없거나 다른 DB에 연결된 것이다.

공유 보상 지표는 현재 코드 흐름에 맞춰 별도 `SHARE_REWARD_CLAIMED` 이벤트가 아니라
`SHARE_COMPLETED` 이벤트의 `properties_json.rewardEarned = true`를 기준으로 집계한다.

---

## 5. 가장 쉬운 실행 방법

Metabase 계정/비밀번호로 실행:

```bash
cd /Users/corapark/Documents/p5laris

METABASE_URL=http://localhost:3000 \
METABASE_EMAIL=admin@example.com \
METABASE_PASSWORD='your-password' \
node import-metabase-polaris-dashboard.mjs
```

API key가 있으면 더 좋다.

```bash
cd /Users/corapark/Documents/p5laris

METABASE_URL=http://localhost:3000 \
METABASE_API_KEY='mb_your_api_key' \
node import-metabase-polaris-dashboard.mjs
```

### 5.1 Windows PowerShell / IntelliJ 터미널에서 실행

프로젝트를 IntelliJ로 열었다면, IntelliJ Terminal에서 repo root로 이동한 뒤 실행하면 된다.

```powershell
pwd
```

위 명령 결과가 `C:\Users\1\Desktop\sparta\po-polaris`처럼 repo root면 아래처럼 실행한다.

```powershell
$env:METABASE_URL="http://localhost:3000"
$env:METABASE_EMAIL="admin@example.com"
$env:METABASE_PASSWORD="your-password"

node docs/metabase/import-metabase-polaris-dashboard.mjs docs/metabase/METABASE_POLARIS_DASHBOARD_IMPORT.json
```

API key를 쓰는 경우:

```powershell
$env:METABASE_URL="http://localhost:3000"
$env:METABASE_API_KEY="mb_your_api_key"

node docs/metabase/import-metabase-polaris-dashboard.mjs docs/metabase/METABASE_POLARIS_DASHBOARD_IMPORT.json
```

---

## 6. DB id를 직접 넣는 방법

Metabase DB 이름이 다르면 자동 매칭이 실패할 수 있다.
그럴 때는 DB id를 직접 넣는다.

```bash
cd /Users/corapark/Documents/p5laris

METABASE_URL=http://localhost:3000 \
METABASE_EMAIL=admin@example.com \
METABASE_PASSWORD='your-password' \
EVENT_LOG_DATABASE_ID=2 \
USERS_DATABASE_ID=3 \
AI_DATABASE_ID=4 \
node import-metabase-polaris-dashboard.mjs
```

Windows PowerShell에서는 아래처럼 넣는다.

```powershell
$env:METABASE_URL="http://localhost:3000"
$env:METABASE_EMAIL="admin@example.com"
$env:METABASE_PASSWORD="your-password"
$env:EVENT_LOG_DATABASE_ID="2"
$env:USERS_DATABASE_ID="3"
$env:AI_DATABASE_ID="4"

node docs/metabase/import-metabase-polaris-dashboard.mjs docs/metabase/METABASE_POLARIS_DASHBOARD_IMPORT.json
```

DB id는 Metabase Admin > Databases에서 DB 상세 URL을 보면 알 수 있다.

예:

```text
http://localhost:3000/admin/databases/2
```

여기서 `2`가 database id다.

---

## 7. Import되면 생기는 것

Collection:

```text
📊 Polaris 반짝 분석실 (YYYY-MM-DD HH:mm)
```

Dashboards:

```text
🌟 Polaris 한눈에 보는 성장 리포트
🌱 사용자 성장과 첫 경험 리포트
🎯 미션 취향과 완료 흐름 리포트
📣 공유와 확산 리포트
🪙 별조각과 상점 리포트
🤖 AI 품질 리포트
🧪 로그 데이터 건강검진 리포트
```

Cards:

```text
🌟 Polaris 오늘의 반짝 리포트
👥 오늘의 반짝 사용자
✨ 새 친구 가입
🔥 핵심 행동 친구들
🎯 미션 성공 온도
🌱 사용자 성장과 첫 경험
📈 사용자 성장 산책길
🚀 첫 경험 계단
🎯 미션 취향과 완료 흐름
🧭 미션 마음 온도
🏷️ 인기 미션 취향 지도
📣 공유와 상점 행동
📨 공유 카드 여행
🌐 공유 채널 지도
🪙 별조각 지갑 흐름
🛒 인기 상점 아이템
🤖 AI와 데이터 건강
🤖 AI 컨디션 체크
⚠️ AI Fallback 알림판
🧪 로그 건강검진
🌱 가입에서 온보딩까지
🎚️ 온보딩 미션 강도 취향
🔁 D1 재방문 친구들
⛰️ 난이도별 미션 성공
✍️ 완료 질문 제출률
⏱️ 미션 완료까지 걸린 시간
📬 공유와 상점 하루 흐름
🎁 공유 보상 획득률
🛍️ 상점 구매 하루 흐름
💞 구매 친구 vs 비구매 친구
⭐ 별조각 사용 내역
🧯 최근 AI Fallback 비율
🧠 모델별 AI 상태
⏳ AI 응답 시간
🧺 이벤트 타입 바구니
🏭 서비스별 로그 생산량
🐢 늦게 들어온 이벤트
```

현재 JSON은 query card 32개와 dashboard 7개를 만든다.
각 dashboard 안에는 구역 제목용 text card도 같이 들어간다.

여기서 `🌟`, `🌱`, `🎯`, `📣`, `🪙`, `🤖`, `🧪`로 시작하는 큰 제목 항목은 Metabase의 text card다.
Grafana식 panel은 아니지만, dashboard 안에서 구역 제목처럼 보여서 훨씬 읽기 좋다.

---

## 8. 대시보드 구성 의도

첫 번째 dashboard는 발표용 통합 화면이다.

```text
오늘의 반짝 사용자 / 새 친구 가입 / 핵심 행동 친구들 / 미션 성공 온도
```

나머지 dashboard는 실무 분석용으로 나뉜다.

```text
🌱 사용자 성장과 첫 경험:
  가입, 온보딩, 미션 강도 취향, D1 재방문

🎯 미션 취향과 완료 흐름:
  미션 거절률, 완료율, 카테고리, 난이도, 완료 질문 이탈

📣 공유와 확산:
  공유 카드 생성, 공유 완료, platform, 보상 획득

🪙 별조각과 상점:
  별조각 발행/소비, 아이템 구매, 인기 아이템, 구매 유저 행동

🤖 AI 품질:
  fallback 비율, 오류 원인, 모델별 상태, latency

🧪 로그 데이터 건강검진:
  수집 지연, user_id 누락, 이벤트 타입, source_service
```

아래는 비즈니스/마케팅/운영 품질이다.

```text
공유 전환
채널별 공유 성과
별조각 경제
인기 아이템
AI fallback 품질
이벤트 로그 데이터 품질
```

포트폴리오에서는 이렇게 말하면 좋다.

```text
운영 로그를 단순 저장하지 않고,
Metabase 대시보드로 연결해서 사용자 유입, 핵심 행동, 미션 funnel,
공유 전환, 별조각 경제, AI fallback 품질, 로그 데이터 품질을
통합 화면과 세부 화면으로 나눠 볼 수 있게 구성했습니다.
```

---

## 9. Import 후 손으로 다듬으면 좋은 것

Metabase API는 card와 dashboard를 만들 수 있지만, UI에서 마지막 polish를 하면 훨씬 예쁘다.

- Dashboard 상단에 date filter 추가
- 각 line chart의 색상 통일
- scalar card의 비교 기준을 "이전 날짜 대비"로 추가
- 카드 제목이 너무 길면 줄이기
- 발표 캡처용으로 browser zoom 90% 또는 100% 맞추기
- 데이터가 없는 card는 숨기거나 설명 text card 추가

특히 date filter는 UI로 붙이는 것이 안전하다.

---

## 10. 실패했을 때 체크

### 10.1 로그인 실패

```text
Metabase login 실패
```

확인:

- `METABASE_URL`이 맞는가?
- `METABASE_EMAIL`이 맞는가?
- `METABASE_PASSWORD`에 특수문자가 있으면 따옴표로 감쌌는가?

### 10.2 DB를 못 찾음

```text
DB 'eventLog'를 자동으로 찾지 못했습니다.
```

해결:

- Metabase DB 표시 이름을 `Polaris Event Log`로 바꾼다.
- 또는 `EVENT_LOG_DATABASE_ID=숫자`를 직접 넣는다.

### 10.3 SQL 실행 실패

가능성이 큰 원인:

- Flyway migration이 아직 적용되지 않았다.
- `v_daily_user_activity` 같은 view가 없다.
- DB 권한이 SELECT를 허용하지 않는다.
- `users`, `ai`, `event_log` DB 연결이 서로 빠져 있다.

### 10.4 dashboard card 배치 실패

Metabase 버전별로 dashboard card API가 조금 다를 수 있다.
script는 아래 순서로 fallback한다.

```text
PUT /api/dashboard/:id/cards
-> PUT /api/dashboard/:id
-> POST /api/dashboard/:id/cards
```

그래도 실패하면 card는 생성됐지만 dashboard 배치만 실패했을 수 있다.
그 경우 Metabase UI에서 생성된 질문들을 dashboard에 직접 추가하면 된다.

---

## 11. 운영에서 조심할 점

운영 DB에 admin 계정으로 붙이지 않는다.

```text
metabase_readonly 계정
SELECT 권한만 부여
가능하면 view/materialized view 중심 조회
가능하면 read replica 또는 analytics DB 사용
```

Metabase는 편하게 SQL을 날릴 수 있는 도구이기 때문에, 잘못된 쿼리 하나가 운영 DB에 부담을 줄 수 있다.
그래서 read-only와 statement timeout을 같이 잡는 것이 좋다.

---

## 12. 공식 문서 기준

- [Metabase API](https://www.metabase.com/docs/latest/api)
- [Working with the Metabase API](https://www.metabase.com/learn/metabase-basics/administration/administration-and-operation/metabase-api)
- [Metabase Serialization](https://www.metabase.com/docs/latest/installation-and-operation/serialization)

핵심 정리:

- dashboard는 `/api/dashboard` 계열 API로 다룬다.
- question은 API에서는 card라고 부르며 `/api/card`로 만든다.
- 공식 serialization import/export는 Pro/Enterprise 기능이고 YAML/tgz 기반이다.
- 무료 self-host에서는 API로 JSON 템플릿을 적용하는 방식이 현실적이다.
