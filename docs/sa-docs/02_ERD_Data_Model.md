# 02_ERD_Data_Model

> 기준일: 2026-06-04
> 이 문서는 현재 backend migration 기준으로 정리한다. API 응답에서 URL로 조립되는 값이 있더라도 DB에는 식별자, asset key, object key를 우선 저장한다.

---

# 1. 테이블 상세

## 1.1 `users`

사용자 계정 정보를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 사용자 ID |
| email | varchar unique | 이메일 |
| nickname | varchar | 닉네임 |
| provider | varchar | LOCAL / GOOGLE / KAKAO |
| refresh_token | varchar(512) nullable | OAuth refresh token |
| role | varchar | USER / ADMIN |
| status | varchar | ACTIVE / WITHDRAWN / BLOCKED |
| weather_region_code | varchar(50) nullable | 사용자가 선택한 날씨 권역 코드 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(email)
index(status)
index(created_at)
```

### 비고

```
현재는 사용자 프로필 상세값을 users에 모두 넣지 않는다.
온보딩 설문 기반 개인화 데이터는 onboarding_profiles에서 관리한다.
별조각 잔액은 wallets에서 관리한다.
```

---

## 1.2 `onboarding_profiles`

캐릭터가 선택지형 설문으로 수집한 초기 개인화 데이터를 저장한다.

현재는 질문 마스터 테이블을 만들지 않고, 정해진 설문 결과를 하나의 프로필 테이블에 저장한다. 질문지를 동적으로 운영하는 기능은 현재 운영 범위가 아니므로 `survey_questions` 같은 테이블은 만들지 않는다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 온보딩 프로필 ID |
| user_id | bigint FK unique | 사용자 ID |
| living_type | varchar nullable | LIVING_ALONE / WITH_FAMILY / WITH_ROOMMATE / OTHER |
| wake_up_time | varchar nullable | 예: 07:00, UNKNOWN |
| sleep_time | varchar nullable | 예: 24:00, UNKNOWN |
| preferred_mission_time | varchar nullable | MORNING / AFTERNOON / EVENING / NIGHT / ANYTIME |
| routine_goal | varchar nullable | WAKE_UP / CLEAN_ROOM / GO_OUT / SELF_CARE / STUDY / LIGHT_ACTIVITY |
| activity_preference | varchar nullable | INDOOR / OUTDOOR / BOTH |
| mission_intensity | varchar nullable | VERY_LIGHT / LIGHT / NORMAL |
| onboarding_version | int | 온보딩 응답 구조 버전 |
| routine_goals_json | jsonb nullable | 사용자가 고른 루틴 목표 목록 |
| preferred_time_slots_json | jsonb nullable | 선호 미션 시간대 목록 |
| mission_place_contexts_json | jsonb nullable | 미션 수행 가능 장소/상황 목록 |
| avoided_mission_tags_json | jsonb nullable | 피하고 싶은 미션 태그 목록 |
| answers_json | jsonb nullable | 전체 설문 응답 snapshot |
| completed | boolean | 온보딩 완료 여부 |
| completed_at | timestamp nullable | 온보딩 완료 시각 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 설문 예시

```
Q1. 지금 만들고 싶은 루틴은 무엇인가요? (최대 3개)
- HYDRATION_MEAL
- SPACE_RESET
- LIGHT_MOVEMENT
- EXERCISE_HABIT
- REST_RECOVERY
- MOOD_RECORD
- FOCUS_START
- SOCIAL_LIGHT
- OUTDOOR_SUNLIGHT

Q2. 미션을 받기 편한 시간대는 언제인가요? (최대 5개)
- MORNING
- AFTERNOON
- EVENING
- NIGHT
- ANYTIME

Q3. 미션을 하기 편한 장소나 상황은 어디인가요? (최대 3개)
- HOME
- WORK_SCHOOL
- COMMUTE
- OUTSIDE
- BED_REST

Q4. 미션 강도는 어느 정도가 좋아요? (1개)
- VERY_LIGHT
- LIGHT
- NORMAL

Q5. 피하고 싶은 미션이 있나요? (최대 5개)
- OUTDOOR
- SOCIAL_CONTACT
- HEAVY_MOVEMENT
- LONG_WRITING
- NOISY_ACTION
- NONE
```

### 제약 / 인덱스

```
unique(user_id)
index(completed)
```

---

## 1.3 `character_types`

현재 제공하는 캐릭터 3종의 기본 정보를 저장한다.

캐릭터는 담당 기능이 다른 존재가 아니라, 성격·말투·정서가 다른 애착형 페르소나다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 캐릭터 타입 ID |
| code | varchar unique | NOVA / MUMU / JJORY |
| name | varchar | 노바 / 무무 / 쪼리 |
| summary | varchar | 선택 화면 한 줄 소개 |
| personality | text | 성격 설명 |
| speech_style | text | 말투 설명 |
| intro_message | text | 선택 화면 소개 문구 |
| sample_line | varchar | 대표 대사 |
| active | boolean | 활성 여부 |
| sort_order | int | 노출 순서 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 현재 seed

| code | name | summary |
| --- | --- | --- |
| NOVA | 노바 | 자기가 별이었다는 걸 까먹은 별알 |
| MUMU | 무무 | “무…”밖에 못 하는 작은 별나무 |
| JJORY | 쪼리 | 현관까지 가면 세계여행이라고 믿는 별쥐 |

### 제약 / 인덱스

```
unique(code)
index(active, sort_order)
```

## 1.3.1 `character_assets`

| 컬럼                | 타입 | 설명 |
|-------------------| --- | --- |
| id                | bigint PK | 캐릭터 에셋 ID |
| character_type_id | bigint FK | 캐릭터 타입 ID |
| asset_type        | varchar | IDLE / HAPPY / SLEEPY / HUNGRY / LOW_ENERGY / LONELY |
| asset_url         | text | 캐릭터 이미지 asset key |

---

## 1.3.2 `skin_assets`

스킨 아이템을 장착했을 때 사용할 캐릭터별·상태별 에셋을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 스킨 에셋 ID |
| item_id | bigint | 스킨 아이템 ID |
| character_type_id | bigint FK | 캐릭터 타입 ID |
| asset_type | varchar | IDLE / HAPPY / SLEEPY / HUNGRY / LOW_ENERGY / LONELY |
| asset_url | text | 스킨 이미지 asset key |

### 제약 / 인덱스

```
unique(item_id, character_type_id, asset_type)
index(item_id, character_type_id)
```

---

## 1.4 `user_characters`

사용자가 생성한 캐릭터를 저장한다.

현재는 사용자가 활성 캐릭터 1개를 키우는 구조로 시작한다.

| 컬럼 | 타입          | 설명                     |
| --- |-------------|------------------------|
| id | bigint PK   | 캐릭터 ID                 |
| user_id | bigint FK   | 사용자 ID                 |
| character_type_id | bigint FK   | 캐릭터 타입                 |
| name | varchar(10) | 캐릭터 이름                 |
| level | int         | 레벨                     |
| exp | int         | 누적 경험치                 |
| fullness | int | 포만감, 0 ~ 100 |
| energy | int | 에너지, 0 ~ 100 |
| affection | int | 애정도, 0 ~ 100 |
| active | boolean     | 현재 활성 캐릭터 여부           |
| equipped_skin_id | bigint nullable | 장착한 스킨 아이템 ID |
| last_stat_decreased_at | timestamp nullable | 마지막 자동 스탯 감소 처리 시각 |
| created_at | timestamp   | 생성일                    |
| updated_at | timestamp   | 수정일                    |

### 상태 3개

| 상태 | 의미 |
| --- | --- |
| fullness | 포만감. FEED / 별사탕밥으로 회복 |
| energy | 에너지. SLEEP / 구름 베개로 회복 |
| affection | 애정도. PLAY / 별 장난감으로 회복 |

### 제약 / 인덱스

```
name length <= 10
check(fullness between 0 and 100)
check(energy between 0 and 100)
check(affection between 0 and 100)
index(user_id, active)
index(character_type_id)
partial unique(user_id) where active = true
```

### 비고

```
사용자당 활성 캐릭터는 1개만 허용한다.
스킨 장착 정보는 user_characters.equipped_skin_id가 단일 소스다.
현재 상태별 노출 이미지는 equipped_skin_id와 skin_assets 또는 character_assets를 조합해 결정한다.
```

---

## 1.5 `character_care_logs`

밥 주기, 재우기, 놀아주기 등 캐릭터 상태 관리 기록을 저장한다.

| 컬럼 | 타입              | 설명 |
| --- |-----------------| --- |
| id | bigint PK       | 돌봄 로그 ID |
| user_id | bigint FK       | 사용자 ID |
| character_id | bigint FK       | 캐릭터 ID |
| item_id | bigint nullable | 사용한 소모성 아이템 ID |
| action_type | varchar         | FEED / SLEEP / PLAY |
| before_state_json | text nullable | 돌봄 전 상태 snapshot |
| after_state_json | text nullable | 돌봄 후 상태 snapshot |
| idempotency_key | varchar unique | 돌봄 액션 중복 처리 방지 키 |
| created_at | timestamp       | 생성일 |

### 현재 돌봄 액션

| action_type | 설명 |
| --- | --- |
| FEED | 밥 주기 |
| SLEEP | 재우기 |
| PLAY | 놀아주기 |

### 제약 / 인덱스

```
unique(idempotency_key)
index(user_id, created_at)
index(character_id, created_at)
```

---

## 1.5.1 `character_exp_logs`

캐릭터 경험치 지급 이력을 저장한다. 돌봄, 미션 완료, 재처리 요청이 같은 출처로 여러 번 들어와도 중복 경험치를 지급하지 않기 위한 멱등성 저장소다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 경험치 로그 ID |
| user_id | bigint | 사용자 ID |
| character_id | bigint | 사용자 캐릭터 ID |
| source_type | varchar(50) | 경험치 출처. 예: `CARE`, `MISSION_COMPLETION` |
| source_id | bigint | 출처 도메인의 ID |
| idempotency_key | varchar(120) unique | 경험치 지급 멱등키 |
| exp_amount | int | 지급 경험치 |
| before_exp | int | 지급 전 누적 경험치 |
| after_exp | int | 지급 후 누적 경험치 |
| before_level | int | 지급 전 레벨 |
| after_level | int | 지급 후 레벨 |
| created_at | timestamp | 생성일 |

### 제약 / 인덱스

```
unique(idempotency_key)
unique(source_type, source_id)
check(exp_amount > 0)
check(before_exp >= 0)
check(after_exp >= 0)
check(before_level >= 1)
check(after_level >= 1)
index(character_id, created_at)
index(user_id, created_at)
```

---

## 1.5.2 `character_story_fragments`

캐릭터 상호작용과 별친구 대화 context에 사용할 서사 조각 seed를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 서사 조각 ID |
| memory_key | varchar(120) unique | 프론트/해금 기록에서 사용하는 기억 조각 key |
| character_type_code | varchar(30) | `MUMU`, `NOVA`, `JJORY`, `COMMON` |
| min_level | int | 노출 가능한 최소 캐릭터 레벨 |
| fragment_type | varchar(30) | `COMMON`, `LORE`, `EASTER_EGG` |
| trigger_type | varchar(30) | `TAP`, `LEVEL_UP`, `LOW_HUNGER`, `LOW_ENERGY`, `LOW_AFFECTION`, `NIGHT`, `MIDNIGHT` |
| title | varchar(80) | 기억 조각 제목 |
| message | varchar(255) | 캐릭터 원문 대사 |
| interpretation | varchar(500) | 화면 표시용 해석/보조 문장 |
| story_text | text | 기억 조각 본문 |
| sort_order | int | 정렬 순서 |
| active | boolean | 사용 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(memory_key)
check(character_type_code in ('MUMU', 'NOVA', 'JJORY', 'COMMON'))
check(fragment_type in ('COMMON', 'LORE', 'EASTER_EGG'))
check(trigger_type in ('TAP', 'LEVEL_UP', 'LOW_HUNGER', 'LOW_ENERGY', 'LOW_AFFECTION', 'NIGHT', 'MIDNIGHT'))
index(character_type_code, trigger_type, min_level, active)
```

---

## 1.5.3 `user_character_story_unlocks`

사용자 캐릭터별로 해금된 LORE/EASTER_EGG 기억 조각을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 해금 ID |
| user_id | bigint | 사용자 ID |
| user_character_id | bigint FK | 사용자 캐릭터 ID |
| story_fragment_id | bigint FK | 해금된 `character_story_fragments.id` |
| memory_key | varchar(120) | 해금된 기억 조각 key |
| unlocked_level | int | 해금 당시 캐릭터 레벨 |
| unlocked_at | timestamp | 해금 시각 |
| created_at | timestamp | 생성일 |

### 제약 / 인덱스

```
unique(user_id, user_character_id, story_fragment_id)
index(user_id, user_character_id, unlocked_at)
```

---

## 1.5.4 `share_cards`

내 캐릭터 공유 카드의 발급 이력을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 공유 카드 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 캐릭터 ID |
| headline | varchar nullable | 카드에 삽입된 한 줄 각오/메시지 |
| image_url | text nullable | 렌더링된 카드 이미지 object key |
| share_url | varchar | 공유 식별자. API에서는 shareId로 사용 |

### 제약 / 인덱스
```
partial unique(user_id, image_url) where image_url is not null
```

운영 주의:

- character V10은 `share_cards.image_url`의 full URL을 object key로 정규화한다.
- character V12는 정규화된 `(user_id, image_url)` 기준 unique index를 생성한다.
- V12 적용 전에는 아래 쿼리 결과가 0건인지 확인한다. 결과가 있으면 중복을 정리한 뒤 V12를 적용한다.

```sql
SELECT
    user_id,
    image_url,
    COUNT(*) AS duplicate_count,
    ARRAY_AGG(id ORDER BY id) AS share_card_ids
FROM share_cards
WHERE image_url IS NOT NULL
GROUP BY user_id, image_url
HAVING COUNT(*) > 1;
```

---

## 1.5.5 `share_logs`

사용자의 공유 시도 이벤트 및 일일 1회 보상 수령 여부를 관리한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 공유 이벤트 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 캐릭터 ID |
| share_card_id | bigint FK | 공유 카드 ID |
| share_type | varchar | 공유 타입 (WEB_SHARE_API, COPY_LINK 등) |
| platform | varchar | 공유 플랫폼 (X, KAKAOTALK, ETC) |
| shared_at | timestamp | 공유 시각 |
| share_date | date | 공유 보상 기준일 |
| reward_star_piece | int | 공유 보상 수량 |
| reward_paid | boolean | wallet 지급 성공 여부. 최초 공유 이벤트 저장 시 false이고 outbox 처리 성공 후 true |
| idempotency_key | varchar unique | 공유 보상 중복 지급 방지 키 |

### 제약 / 인덱스
```
unique(idempotency_key)
index(user_id, share_date)
partial unique(user_id, share_date) where reward_paid = true
```

### 비고

```
공유 클릭 수집 API는 현재 DB 테이블을 만들지 않고 애플리케이션 로그만 남긴다.
공유카드 이미지의 콘텐츠 자체는 신뢰하지 않고, share_logs와 보상 멱등키를 보상 기준으로 사용한다.
오늘 첫 공유 보상 대상이면 share_logs와 character_outbox_events를 같은 트랜잭션에 저장한다.
```

---

## 1.5.6 `character_outbox_events`

character 모듈에서 외부 모듈 호출 또는 향후 Kafka 발행이 필요한 이벤트를 저장하는 공용 outbox 테이블이다.
현재 공유 보상 지급 요청은 `SHARE_REWARD_REQUESTED` 이벤트로 저장하고, user wallet gRPC 지급 실패 시 스케줄러가 재처리한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | outbox 이벤트 ID |
| aggregate_type | varchar | 이벤트가 속한 aggregate 타입. 예: `SHARE_LOG` |
| aggregate_id | bigint | aggregate ID. 공유 보상은 share_logs.id |
| event_type | varchar | 이벤트 타입. 예: `SHARE_REWARD_REQUESTED` |
| payload | jsonb | 이벤트 처리에 필요한 JSON payload |
| idempotency_key | varchar(120) unique | 외부 호출/발행 멱등키 |
| status | varchar | PENDING / PROCESSING / SUCCEEDED / FAILED |
| attempt_count | int | 재시도 횟수 |
| next_attempt_at | timestamp | 다음 처리 가능 시각 |
| last_error_message | text nullable | 마지막 실패 메시지 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(idempotency_key)
check(attempt_count >= 0)
check(status in ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
index(status, next_attempt_at)
index(event_type, status, next_attempt_at)
index(aggregate_type, aggregate_id, event_type)
```

### 공유 보상 payload 예

```json
{
  "userId": 1,
  "rewardStarPiece": 10
}
```

---

## 1.6 `wallets`

사용자의 별조각 잔액을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 지갑 ID |
| user_id | bigint FK unique | 사용자 ID |
| star_piece | int | 별조각 잔액 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약

```
unique(user_id)
check(star_piece >= 0)
```

### 비고

```
별조각은 현재 무료 재화다.
별조각은 미션 완료, 출석, 업적, SNS 공유 보상으로 획득한다.
별조각 구매 기능은 현재 구현하지 않는다.
Mock 구매, Mock 지급, 테스트용 결제 버튼도 만들지 않는다.
```

---

## 1.7 `star_piece_transactions`

별조각 지급/사용 원장을 저장한다.

별조각 잔액이 변경되는 모든 작업은 반드시 이 테이블에 기록한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 거래 ID |
| user_id | bigint FK | 사용자 ID |
| transaction_type | varchar | EARN / SPEND / ADJUST |
| amount | int | 증감량. 사용은 음수 |
| balance_after | int | 거래 후 잔액 |
| reason | varchar | MISSION_REWARD / ITEM_PURCHASE / ATTENDANCE / ACHIEVEMENT / SHARE_REWARD / CARE_ACTION |
| ref_type | varchar nullable | MISSION / ITEM / ATTENDANCE / ACHIEVEMENT / SHARE / CARE |
| ref_id | bigint nullable | 참조 ID |
| idempotency_key | varchar unique nullable | 중복 방지 키 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### reason

| reason | 설명 |
| --- | --- |
| MISSION_REWARD | 미션 완료 보상 |
| ITEM_PURCHASE | 아이템 구매 |
| ATTENDANCE | 출석 보상 |
| ACHIEVEMENT | 업적 보상 (후속 확장) |
| SHARE_REWARD | SNS 공유 시도 보상 |
| CARE_ACTION | 캐릭터 돌봄 액션 별조각 사용 |

### idempotency key 예시

```
MISSION_REWARD:{missionId}
ATTENDANCE:{userId}:{yyyyMMdd}
ACHIEVEMENT:{userAchievementId}
ITEM_PURCHASE:{purchaseRequestId}
SHARE_REWARD:{userId}:{shareDate}
CARE_ACTION:{careLogId}
```

### 제약 / 인덱스

```
unique(idempotency_key)
index(user_id, created_at)
index(reason, created_at)
index(ref_type, ref_id)
```

### 비고

```
MOCK_PURCHASE, PAYMENT_CHARGE, CASH_PURCHASE 같은 reason은 현재 enum에 넣지 않는다.
결제 기능이 실제로 필요한 시점에 별도 migration과 정책 검토 후 추가한다.
```

---

## 1.8 `mission_templates`

AI 미션 생성의 안전한 fallback pool과 기본 미션 후보를 저장한다.

AI가 사용할 수 없는 응답을 반환하거나 외부 provider가 실패하면, 활성 seed 미션 템플릿과 fallback 문구를 기준으로 사용자에게 미션을 제안한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 미션 템플릿 ID |
| base_title | varchar | 기본 미션 제목 |
| base_description | text | 기본 설명 |
| category | varchar | BASIC_ROUTINE / SPACE_RESET / BODY_CARE / OUTDOOR_LIGHT / MIND_RECORD / REST_RECOVERY / SOCIAL_LIGHT |
| difficulty | varchar | EASY / NORMAL / CHALLENGE |
| reward_star_piece | int | 기본 별조각 보상. EASY 10, NORMAL 15, CHALLENGE 30 기준 |
| active | boolean | 활성 여부 |
| fallback_character_message | text | AI 실패 시 사용할 기본 캐릭터 제안 문구 |
| fallback_question | text | AI 실패 시 사용할 기본 완료 질문 |
| fallback_completion_response | text | AI 실패 시 사용할 기본 완료 반응 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
index(active, category, difficulty)
```

### 현재 seed 예시

| category | base_title | reward_star_piece |
| --- | --- | --- |
| BASIC_ROUTINE | 물 한 컵 마시기 | 10 |
| BASIC_ROUTINE | 양치하기 | 10 |
| SPACE_RESET | 창문 3분 열기 | 10 |
| SPACE_RESET | 책상 위 물건 하나 치우기 | 10 |
| OUTDOOR_LIGHT | 하늘 한 번 보고 오기 | 10 |
| MIND_RECORD | 오늘 기분 한 단어 적기 | 10 |

---

## 1.9 `prompt_templates`

AI 미션 생성과 캐릭터 말투 변환에 사용할 프롬프트 템플릿을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 프롬프트 ID |
| name | varchar | 템플릿 이름 |
| category | varchar | MISSION_GENERATION / CHARACTER_TONE / COMPLETION_QA / FALLBACK |
| template | text | 프롬프트 본문 |
| version | int | 버전 |
| active | boolean | 활성 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

프롬프트는 DB에 저장하고 category, active, version 기준으로 조회한다.

### 제약 / 인덱스

```
unique(name, version)
index(category, active)
```

---

## 1.10 `ai_mission_generations`

AI가 미션 후보와 캐릭터 말투 문구를 생성한 결과를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | AI 생성 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 제안한 캐릭터 ID |
| prompt_template_id | bigint FK | 사용한 프롬프트 ID |
| request_id | varchar unique | AI 생성 요청 멱등/추적 ID |
| request_hash | varchar | request_id를 제외한 요청 본문 SHA-256 |
| request_context_json | jsonb | 온보딩, 최근 미션, 거절, 날씨, 캐릭터 상태 등 입력 요약 |
| response_json | jsonb | AI 구조화 응답 |
| selected_template_id | bigint nullable | fallback 또는 기준 후보로 사용한 seed 미션 템플릿 |
| status | varchar | SUCCESS / FALLBACK / FAILED |
| fallback_used | boolean | fallback 사용 여부 |
| model | varchar | 사용 모델 |
| error_type | varchar nullable | TIMEOUT / RATE_LIMIT / RATE_LIMIT_UNAVAILABLE / INVALID_OUTPUT / POLICY_VIOLATION / PROVIDER_ERROR / UNKNOWN |
| created_at | timestamp | 생성일 |


prompt_template_id는 현재 prompt_templates 테이블의 활성 미션 생성 프롬프트를 참조한다.
외부 provider 호출 결과, fallback 결과, rate limit으로 인해 외부 호출을 생략한 결과를 같은 테이블에 저장한다.

### 제약 / 인덱스

```
unique(request_id)
check(length(request_hash) = 64)
index(user_id, created_at)
index(status, created_at)
index(model, created_at)
```

### 비고

```
AI 응답은 저장 전에 구조화 출력 검증을 통과해야 한다.
AI가 없는 미션 템플릿이나 잘못된 보상을 생성하면 fallback으로 대체한다.
같은 request_id와 같은 request_hash가 다시 들어오면 기존 생성 결과를 반환한다.
같은 request_id가 다른 request_hash와 함께 들어오면 멱등키 오사용으로 보고 충돌 처리한다.
```

---

## 1.11 `user_missions`

사용자에게 실제 제안된 미션 인스턴스다.

미션은 하루에 여러 개가 stack으로 쌓이지만, 사용자에게는 한 번에 하나의 현재 미션을 보여준다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 미션 ID |
| user_id | bigint FK | 사용자 ID |
| character_id | bigint FK | 미션을 제안한 캐릭터 ID |
| mission_template_id | bigint FK nullable | 미션 템플릿 ID |
| ai_generation_id | bigint FK nullable | AI 생성 로그 ID |
| mission_date | date | 기준일 |
| stack_order | int | 해당 날짜의 몇 번째 제안인지 |
| title | varchar | 미션 제목 |
| description | text | 미션 설명 |
| character_message | text | 캐릭터 말투 제안 문구 |
| completion_character_response | text nullable | 완료 후 캐릭터 반응 문구 |
| category | varchar | 미션 카테고리 |
| difficulty | varchar | 난이도 |
| reward_star_piece | int | 보상 별조각 |
| status | varchar | GENERATED / OFFERED / ANSWERING / COMPLETED / REJECTED / EXPIRED |
| offered_at | timestamp nullable | 제안 시각 |
| completion_started_at | timestamp nullable | 완료 질문 답변 시작 시각 |
| completed_at | timestamp nullable | 완료 시각 |
| rejected_at | timestamp nullable | 거절 시각 |
| expired_at | timestamp nullable | 만료 시각 |
| idempotency_key | varchar unique nullable | 완료 보상 중복 지급 방지 marker |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 상태

| status | 설명 |
| --- | --- |
| GENERATED | 생성되었지만 아직 사용자에게 표시 전 |
| OFFERED | 현재 또는 과거에 사용자에게 제안됨 |
| ANSWERING | 사용자가 완료를 눌렀고 1문항 답변 중 |
| COMPLETED | 답변 제출 후 미션 완료 |
| REJECTED | 사용자가 거절 |
| EXPIRED | 날짜 변경 등으로 만료 |

### 제약 / 인덱스

```
unique(user_id, mission_date, stack_order)
unique(idempotency_key)
partial unique(user_id, mission_date) where status in ('OFFERED', 'ANSWERING')
index(user_id, mission_date, status)
```

### 정책

```
하루 미션 완료 보상은 application에서 20회로 제한한다.
하루 미션 거절은 application에서 10회로 제한한다.
CHALLENGE 난이도 미션은 하루 1회로 제한한다.
현재 미션은 해당 날짜의 최신 OFFERED 또는 ANSWERING 상태 미션으로 조회한다.
미션 완료 보상은 missionId 기준 1회만 지급한다.
idempotency_key가 있으면 mission 관점에서는 wallet 보상 지급 요청이 성공했거나 outbox가 SUCCEEDED 상태로 확정된 것으로 본다.
AI 미션 생성이 성공하면 ai_generation_id와 AI가 반환한 미션 제목/설명/문구를 저장하고, 실패하면 mission_templates fallback 미션과 문구를 사용한다.
```

---

## 1.12 `mission_completion_answers`

미션 완료 후 캐릭터가 던지는 1문항 질의응답을 저장한다.

단순 완료 클릭만으로 보상이 지급되지 않도록, 사용자는 미션과 관련된 질문 1개에 텍스트로 답변해야 한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 완료 답변 ID |
| mission_id | bigint FK | 미션 ID |
| user_id | bigint FK | 사용자 ID |
| question_text | text | 캐릭터가 던진 질문 |
| answer_text | text nullable | 사용자 답변 |
| answered_at | timestamp nullable | 답변 시각 |
| created_at | timestamp | 생성일 |

### 질문 예시

```
산책 미션:
오늘 하늘 색깔은 어땠어?

물 마시기 미션:
물 마시고 나서 기분이 조금 달라졌어?

정리 미션:
어떤 물건을 치웠어?
```

### 제약 / 인덱스

```
unique(mission_id)
```

### 보상 지급 조건

```
mission_completion_answers.answer_text가 저장된다.
user_missions.status를 COMPLETED로 변경한다.
MISSION_REWARD:{missionId} 멱등키로 mission_outbox_events를 생성하거나 기존 row를 재사용한다.
wallet 모듈 보상 요청이 성공하면 user_missions.idempotency_key에 같은 marker를 저장한다.
일시 실패하면 outbox status와 next_attempt_at을 갱신하고 스케줄러가 재처리한다.
```

---

## 1.12.1 `mission_feedbacks`

미션 거절 이유와 만족/불만족 피드백을 저장한다.

피드백은 보상 지급 조건이 아니라 개인화와 회피 신호 분석을 위한 데이터다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 피드백 ID |
| user_id | bigint | 사용자 ID |
| mission_id | bigint FK | 미션 ID |
| feedback_type | varchar | REJECTION / SATISFACTION |
| reaction | varchar nullable | LIKE / DISLIKE |
| reason_code | varchar nullable | NOT_NOW / TOO_HARD / NOT_INTERESTED / ALREADY_DONE / LOCATION_MISMATCH / MOOD_MISMATCH / REPEAT / JUST_SKIP / OTHER |
| reason_text | varchar(100) nullable | 사용자가 직접 적은 짧은 이유 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(user_id, mission_id, feedback_type)
check(feedback_type in ('REJECTION', 'SATISFACTION'))
check(reaction is null or reaction in ('LIKE', 'DISLIKE'))
index(user_id, created_at desc)
```

---

## 1.12.2 `mission_outbox_events`

mission 모듈에서 외부 모듈 호출 또는 향후 Kafka 발행이 필요한 이벤트를 저장하는 공용 outbox 테이블이다.

미션 완료 보상 지급 요청은 `MISSION_REWARD_REQUESTED` 이벤트로 저장하고, user wallet gRPC 지급 실패 시 스케줄러가 재처리한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | outbox 이벤트 ID |
| aggregate_type | varchar | 이벤트가 속한 aggregate 타입. 예: `MISSION` |
| aggregate_id | bigint | aggregate ID. 미션 보상은 user_missions.id |
| event_type | varchar | 이벤트 타입. 예: `MISSION_REWARD_REQUESTED` |
| payload | jsonb | 이벤트 처리에 필요한 JSON payload |
| idempotency_key | varchar(120) unique | 외부 호출/발행 멱등키 |
| status | varchar | PENDING / PROCESSING / SUCCEEDED / FAILED |
| attempt_count | int | 재시도 횟수 |
| next_attempt_at | timestamp | 다음 처리 가능 시각 |
| last_error_message | text nullable | 마지막 실패 메시지 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(idempotency_key)
check(attempt_count >= 0)
check(status in ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
index(event_type, status, next_attempt_at)
```

### 정책

```
미션 보상 지급은 aggregate_id와 idempotency_key 기준으로 멱등 처리한다.
일시 실패한 보상은 next_attempt_at 이후 스케줄러가 재처리한다.
영구 실패는 attempt_count 상한 이후 FAILED로 남기고 운영자가 확인할 수 있게 로그를 남긴다.
```

---

## 1.12.3 `user_memories`

미션 완료 답변과 피드백에서 개인화에 쓸 수 있는 사용자 기억을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 사용자 기억 ID |
| user_id | bigint | 사용자 ID |
| source_type | varchar | MISSION_COMPLETION_ANSWER / MISSION_FEEDBACK |
| source_id | bigint | 원천 데이터 ID |
| memory_type | varchar | MISSION_COMPLETION / MISSION_REJECTION / MISSION_SATISFACTION |
| content | text | 개인화 검색에 사용할 요약 문장 |
| metadata_json | jsonb | 원천 미션, 카테고리, 난이도 등 부가 정보 |
| importance | int | 중요도. 0~100 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(source_type, source_id, memory_type)
check(source_type in ('MISSION_COMPLETION_ANSWER', 'MISSION_FEEDBACK'))
check(memory_type in ('MISSION_COMPLETION', 'MISSION_REJECTION', 'MISSION_SATISFACTION'))
check(importance between 0 and 100)
index(user_id, created_at desc)
index(user_id, memory_type, created_at desc)
```

---

## 1.12.4 `user_memory_embeddings`

`user_memories`를 vector 검색에 사용할 수 있도록 embedding 생성 상태와 vector 값을 저장한다.

PostgreSQL `pgvector` 확장을 사용하며, embedding은 `gemini-embedding-001` 모델의 768차원 vector로 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | embedding ID |
| user_memory_id | bigint FK | 사용자 기억 ID |
| user_id | bigint | 사용자 ID |
| embedding_model | varchar(80) | embedding 모델명 |
| embedding_dimension | int | embedding 차원. 현재 768 |
| embedding | vector(768) nullable | 정규화된 embedding vector |
| status | varchar | PENDING / PROCESSING / COMPLETED / FAILED |
| attempt_count | int | 재시도 횟수 |
| next_attempt_at | timestamp | 다음 처리 가능 시각 |
| last_error_message | text nullable | 마지막 실패 메시지 |
| embedded_at | timestamp nullable | embedding 완료 시각 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(user_memory_id, embedding_model, embedding_dimension)
check(status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
check(embedding_dimension = 768)
check(attempt_count >= 0)
index(status, next_attempt_at, id)
index(user_id, status)
```

---

## 1.13 `mission_interactions` → 후속 확장

미션 조회, 거절, 완료 등 사용자 반응 데이터를 저장한다.

개인화 추천, 거절률 분석, 완료율 분석에 사용한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 상호작용 ID |
| mission_id | bigint FK | 미션 ID |
| user_id | bigint FK | 사용자 ID |
| interaction_type | varchar | VIEWED / COMPLETION_STARTED / COMPLETED / REJECTED / EXPIRED |
| metadata_json | jsonb nullable | 날씨, 시간대, 캐릭터 상태 등 추가 context |
| created_at | timestamp | 생성일 |

현재 migration에는 포함하지 않았다. 조회/거절/완료 이벤트를 별도 분석 테이블로 쌓아야 할 때 후속 확장한다.

### 제약 / 인덱스

```
index(user_id, created_at)
index(mission_id, interaction_type)
index(interaction_type, created_at)
```

---

## 1.14 `ai_usage_logs`

AI 요청 비용, 지연, 실패율을 추적한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | AI 사용 로그 ID |
| user_id | bigint FK nullable | 사용자 ID |
| request_id | varchar unique | 요청 ID |
| model | varchar | 사용 모델 |
| prompt_tokens | int | 입력 토큰 |
| completion_tokens | int | 출력 토큰 |
| total_tokens | int | 총 토큰 |
| latency_ms | int | 응답 지연 |
| status | varchar | SUCCESS / FAILED / FALLBACK / RATE_LIMITED |
| error_type | varchar nullable | TIMEOUT / RATE_LIMIT / RATE_LIMIT_UNAVAILABLE / INVALID_OUTPUT / POLICY_VIOLATION / PROVIDER_ERROR / UNKNOWN |
| created_at | timestamp | 생성일 |

### 제약 / 인덱스

```
unique(request_id)
index(user_id, created_at)
index(model, created_at)
index(status, created_at)
```

---

## 1.15 `items`

상점에서 구매 가능한 아이템 마스터다.

| 컬럼                | 타입               | 설명                 |
|-------------------|------------------|--------------------|
| id                | bigint PK        | 아이템 ID             |
| name              | varchar          | 아이템명               |
| description       | text nullable    | 아이템 설명             |
| item_type         | varchar          | SKIN / CONSUMABLE  |
| price             | int              | 별조각 가격             |
| effect            | int              | 사용 효과              |
| effect_type       | varchar          | FOOD / REST / PLAY |
| image_url         | varchar nullable | 이미지 경로 또는 URL |
| active            | boolean          | 판매 여부              |
| created_at        | timestamp        | 생성일                |
| updated_at        | timestamp        | 수정일                |
| character_type_id | bigint FK        | 캐릭터 타입 (특정 캐릭터용 아이템 구분) |

### 정책

```
SKIN은 장착형 아이템이다.
CONSUMABLE은 소모성 아이템이다.
소모성 아이템의 effect_type은 FOOD / REST / PLAY를 사용한다.
스킨은 character_type_id로 캐릭터별 전용 스킨을 구분할 수 있다.
아이템 가격은 현금 가격이 아니라 별조각 가격이다.
```

### 제약 / 인덱스

```
현재 items 테이블에는 별도 명시 인덱스를 두지 않는다.
```

---

## 1.16 `user_items`

사용자가 보유한 아이템과 수량을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 보유 아이템 ID |
| user_id | bigint FK | 사용자 ID |
| item_id | bigint FK | 아이템 ID (`items.id` 참조) |
| quantity | int | 보유 수량 (소모성 아이템), 스킨은 항상 1 |
| created_at | timestamp | 최초 획득일 |
| updated_at | timestamp | 수정일 |

> **장착 여부(`equipped`) 컬럼 미존재 — 의도된 설계**
>
> 스킨 장착 정보는 `user_characters.equipped_skin_id` 가 **단일 소스(source of truth)** 입니다.
> `user_items`에 `equipped` 컬럼을 두면 두 모듈 간 이중 저장 문제와 동기화 위험이 발생하므로 제거했습니다.
> 클라이언트는 character 정보의 `equipped_skin_id` 와 아이템 목록의 `item_id` 를 직접 비교하여 장착 상태를 판단합니다 (클라이언트 싱크 방식).

### 제약 / 인덱스

```
unique(user_id, item_id)
```

### 정책

```
장착형 아이템(스킨)은 중복 구매할 수 없다.
소모성 아이템은 같은 row의 quantity를 증가시킨다.
별조각이 부족하면 구매할 수 없다.
아이템 구매와 별조각 차감은 하나의 트랜잭션으로 처리한다.
소모품 사용 시 quantity를 1 감소시키고 item_usage_histories에 이력을 기록한다.
```

---

## 1.16.1 `item_usage_histories`

소모성 아이템 사용 이력을 저장한다.
character 모듈이 돌봄 액션(FEED / SLEEP / PLAY) 수행 시 item 모듈의 gRPC `UseItem` API를 통해 기록된다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 사용 이력 ID |
| user_id | bigint | 사용자 ID |
| user_item_id | bigint FK | 사용된 보유 아이템 ID (`user_items.id` 참조) |
| item_id | bigint FK | 사용된 아이템 ID (`items.id` 참조, 편의 컬럼) |
| quantity | int | 사용 수량 (현재 = 1) |
| ref_type | varchar(50) | 사용 컨텍스트 (예: `CARE_ACTION`) |
| ref_id | bigint | 컨텍스트 PK (예: `character_care_logs.id`) |
| idempotency_key | varchar(100) unique nullable | 중복 사용 방지 멱등키 |
| created_at | timestamp | 사용 시각 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(idempotency_key)
index(user_id)
index(user_item_id)
```

### 정책

```
idempotency_key가 동일한 요청은 중복 처리하지 않고 기존 결과를 반환한다.
character 모듈이 생성하는 멱등키 형식: "care-{careLogId}-item-{itemId}"
아이템 소모 실패 시 돌봄 결과(스탯 변화)는 유지하고 에러 로그만 남긴다.
```

---

## 1.16.2 `item_purchase_histories`

아이템 구매 이력과 별조각 차감 거래 매핑을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 구매 이력 ID |
| user_id | bigint | 사용자 ID |
| user_item_id | bigint FK | 구매 후 보유 아이템 ID |
| item_id | bigint FK | 구매한 아이템 ID |
| quantity | int | 구매 수량 |
| price | int | 구매 시점 단가 |
| star_piece | int | 총 차감 별조각 |
| transaction_id | bigint | star_piece_transactions ID |
| idempotency_key | varchar unique nullable | 구매 요청 중복 처리 방지 키 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(idempotency_key)
check(quantity > 0)
index(user_id)
index(user_item_id)
```

### 정책

```
동일 idempotency_key로 들어온 구매 요청은 같은 구매 결과를 반환한다.
아이템 구매와 별조각 차감은 하나의 트랜잭션으로 처리한다.
```

---


## 1.17 `achievements`  → 후속 확장

업적 마스터를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 업적 ID |
| code | varchar unique | 업적 코드 |
| title | varchar | 업적명 |
| description | text | 설명 |
| achievement_type | varchar | DAILY / WEEKLY / ATTENDANCE / COLLECTION / ITEM / MISSION |
| condition_json | jsonb | 달성 조건 |
| reset_cycle | varchar | NONE / DAILY / WEEKLY |
| reward_star_piece | int | 보상 별조각 |
| active | boolean | 활성 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(code)
index(active, achievement_type)
```

---

## 1.18 `user_achievements` → 후속 확장

사용자별 업적 진행도를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 사용자 업적 ID |
| user_id | bigint FK | 사용자 ID |
| achievement_id | bigint FK | 업적 ID |
| progress | int | 진행도 |
| completed | boolean | 완료 여부 |
| reward_claimed | boolean | 보상 수령 여부 |
| completed_at | timestamp nullable | 완료일 |
| reward_claimed_at | timestamp nullable | 보상 수령일 |
| period_key | varchar nullable | DAILY/WEEKLY 업적 기간 키. 예: 2026-05-13, 2026-W20 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(user_id, achievement_id, period_key)
index(user_id, completed)
index(user_id, reward_claimed)
```

### 비고

```
reset_cycle이 NONE이면 period_key는 'NONE' 또는 null 정책 중 하나로 통일한다.
현재는 'NONE' 문자열을 추천한다.
```

---

## 1.19 `attendance_records`

출석 기록을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 출석 ID |
| user_id | bigint FK | 사용자 ID |
| attendance_date | date | 출석일 |
| streak_count | int | 연속 출석 수 |
| reward_star_piece | int | 출석 보상 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(user_id, attendance_date)
index(user_id, attendance_date)
```
---

## 1.20 `notifications`

앱 내부 알림을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 알림 ID |
| user_id | bigint FK | 수신 사용자 ID |
| character_id | bigint FK nullable | 말한 캐릭터 ID |
| notification_type | varchar | MISSION / CARE / ACHIEVEMENT / ATTENDANCE / SHARE / SYSTEM |
| title | varchar | 제목 |
| message | text | 내용 |
| target_type | varchar nullable | MISSION / CHARACTER / ITEM / ACHIEVEMENT / SHARE |
| target_id | bigint nullable | 이동 대상 ID |
| is_read | boolean | 읽음 여부 |
| read_at | timestamp nullable | 읽은 시각 |
| push_required | boolean | FCM 푸시 발송 대상 여부 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 비고

```
현재 notifications migration에는 별도 명시 인덱스를 두지 않는다.
목록 조회 성능이 필요해지면 user_id, is_read, created_at 조합 인덱스를 추가한다.
```

---

## 1.21 `fcm_device_tokens`

브라우저 또는 앱에서 발급한 FCM registration token을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | FCM 토큰 ID |
| user_id | bigint FK | 사용자 ID |
| fcm_token | text | FCM registration token 원문 |
| token_hash | varchar unique | FCM token SHA-256 해시 |
| platform | varchar | WEB / ANDROID / IOS |
| active | boolean | 활성 여부 |
| token_updated_at | timestamp | 토큰 갱신 시각 |
| deactivated_at | timestamp nullable | 비활성화 시각 |
| deactivated_reason | varchar nullable | LOGOUT / PERMISSION_DENIED / TOKEN_INVALID / USER_DISABLED / STALE / UNKNOWN |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(token_hash)
```

---

## 1.21.1 `notification_push_deliveries`

FCM 푸시 발송 시도와 결과를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 푸시 발송 이력 ID |
| notification_id | bigint | 알림 ID |
| user_id | bigint | 수신 사용자 ID |
| fcm_device_token_id | bigint nullable | 발송 대상 FCM 토큰 ID |
| delivery_status | varchar | PENDING / SENT / FAILED / SKIPPED |
| fcm_message_id | varchar nullable | FCM 발송 성공 시 반환 message id |
| error_code | varchar nullable | FCM 실패 코드 |
| error_message | text nullable | FCM 실패 상세 메시지 |
| attempted_at | timestamp nullable | 발송 시도 시각 |
| sent_at | timestamp nullable | 발송 성공 시각 |
| failed_at | timestamp nullable | 발송 실패 시각 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 비고

```
현재 notification_push_deliveries migration에는 FK 제약과 별도 명시 인덱스를 두지 않는다.
FCM 실패 원인 분석과 운영 추적을 위한 이력 테이블로 사용한다.
```

---

## 1.21.2 `notification_settings`

사용자별 알림 수신 설정과 방해 금지 시간을 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 알림 설정 ID |
| user_id | bigint unique | 사용자 ID |
| push_enabled | boolean | 전체 푸시 알림 허용 여부 |
| daily_push_limit | int | 하루 푸시 최대 발송 수 |
| mission_offer_enabled | boolean | 미션 제안 알림 허용 여부 |
| character_state_enabled | boolean | 캐릭터 상태 알림 허용 여부 |
| daily_reminder_enabled | boolean | 일일 리마인더 허용 여부 |
| quiet_hours_enabled | boolean | 방해 금지 시간 사용 여부 |
| quiet_hours_start | time | 방해 금지 시작 시각 |
| quiet_hours_end | time | 방해 금지 종료 시각 |
| created_at | timestamp | 생성일 |
| updated_at | timestamp | 수정일 |

### 제약 / 인덱스

```
unique(user_id)
```

### 정책

```
방해 금지 시간과 알림 종류별 수신 여부는 백엔드 발송 판단에서 사용한다.
프론트는 설정 조회/수정 UI를 제공하고, 최종 발송 여부는 백엔드가 결정한다.
```

---

## 1.22 `event_logs`

제품 이벤트와 운영 지표 계산용 로그를 저장한다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | bigint PK | 이벤트 ID |
| event_id | uuid unique | 이벤트 고유 식별자 |
| event_type | varchar | 이벤트 타입 |
| source_service | varchar nullable | 이벤트 발생 서비스 |
| user_id | bigint nullable | 사용자 ID |
| anonymous_id | varchar nullable | 비로그인/브라우저 식별자 |
| ref_type | varchar nullable | 참조 타입 |
| ref_id | bigint nullable | 참조 ID |
| properties_json | jsonb nullable | 이벤트 상세 속성 |
| context_json | jsonb nullable | 발생 환경 정보 |
| occurred_at | timestamp | 실제 발생 시각 |
| created_at | timestamp | DB 저장 시각 |

### 주요 event_type

```
USER_SIGNED_UP
ONBOARDING_COMPLETED
CHARACTER_CREATED
MISSION_GENERATED
MISSION_OFFERED
MISSION_REJECTED
MISSION_COMPLETION_SESSION_STARTED
MISSION_COMPLETED
STAR_PIECE_EARNED
STAR_PIECE_SPENT
ITEM_PURCHASED
ITEM_EQUIPPED
CARE_ACTION_PERFORMED
ACHIEVEMENT_COMPLETED
ATTENDANCE_CHECKED
CHARACTER_CARD_SHARED
NOTIFICATION_CREATED
NOTIFICATION_CLICKED
AI_MISSION_GENERATION_FAILED
AI_FALLBACK_USED
```

### 제약 / 인덱스

```
unique(event_id)
```

---

