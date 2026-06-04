# Polaris REST API 명세서

> 기준일: 2026-06-04
> 기준 문서: Polaris v1.0 PRD, 최신 ERD, `polaris` 백엔드 gateway/proto 코드 대조

--- 

## 0. 공통 규칙

### 0.1 URL 규칙

```text
Base Pattern: /api/{domain}/v1/{resource}
```

예시:

```text
/api/auth/v1/google/sessions
/api/character/v1/characters/me
/api/mission/v1/missions/current
/api/item/v1/items
```

### 0.2 표기

| 표시 | 의미 |
|---|---|
| 🔐 | 인증 필요. `Authorization: Bearer {accessToken}` 필요 |
| 💾 | 캐싱 권장 API. 자주 바뀌지 않는 조회성 데이터 |
| ⚠️ | 동시성 민감 API. 중복 지급, 잔액 차감, 수량 차감, 일일 제한 정합성 처리 필요 |

### 0.3 응답 포맷

모든 API 응답은 `ApiResponse`로 감싼다. API별 Response 예시는 반복을 줄이기 위해 `data` 내부만 작성한다.

#### 성공 응답

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

#### 실패 응답

요청 처리에 실패한 경우 `success`는 `false`, `data`는 `null`로 응답한다.  
실패 원인은 `error` 객체에 담아 반환한다.

```json
{
  "success": false,
  "data": null,
  "error": {
    "timestamp": "2026-05-15T13:42:10+09:00",
    "status": 400,
    "code": "MISSION_INVALID_STATUS",
    "message": "현재 상태에서는 미션을 완료할 수 없습니다.",
    "path": "/api/mission/v1/missions/10/completion-answer"
  }
}
```

`retryAfterSeconds`는 기본 필드는 아니고, AI 요청 제한이나 일시적 제한 상황에서만 선택적으로 포함한다.
```json
{
  "success": false,
  "data": null,
  "error": {
    "timestamp": "2026-05-15T13:42:10+09:00",
    "status": 429,
    "code": "AI_RATE_LIMIT_EXCEEDED",
    "message": "잠시 후 다시 시도해 주세요.",
    "path": "/api/mission/v1/missions/current",
    "retryAfterSeconds": 30
  }
}
```

### 0.4 페이지네이션

목록 조회는 cursor 기반을 우선한다.

```json
{
  "items": [],
  "pageInfo": {
    "nextCursor": "eyJpZCI6MTAwfQ==",
    "hasNext": true,
    "size": 20
  }
}
```

### 0.5 Idempotency

중복 요청이 위험한 API는 `idempotencyKey`를 받는다.

대상 예시:

- 미션 생성/제안
- 미션 완료 보상 지급
- 아이템 구매
- 캐릭터 돌봄 액션
- 공유 시도 보상 기록
- 출석 보상 지급

### 0.6 현재 구현 기준

아래 표는 2026-06-04 기준 `polaris` 백엔드가 제공하는 API 계약을 요약한다. 각 상세 섹션은 이 기준에 맞춰 요청/응답 예시를 작성한다.

| 구분 | 현재 API 기준 | 응답/처리 규칙 |
|---|---|---|
| 홈 통합 조회 | `GET /api/home/v1/home` | 홈 화면은 user, wallet, character, currentMission, notifications 요약을 이 API로 조회한다. |
| 날씨 권역 | `GET /api/user/v1/weather-regions`, `GET/PUT /api/user/v1/users/me/weather-region` | 사용자가 직접 선택한 권역을 날씨 기반 미션 context에 사용한다. 미선택 상태이면 mission 서비스의 기본 권역을 사용한다. |
| 현재 캐릭터 | `GET /api/character/v1/characters/me` | `states`, `growth`, `currentAssetUrl`, `assetUrls`, `equippedSkin`을 함께 반환한다. `currentAssetUrl`은 서버가 현재 상태 기준으로 고른 표시용 URL이고, `assetUrls`는 상태별 전환/프리로드용 맵이다. |
| 스킨 장착/해제 | `PUT /api/character/v1/characters/{characterId}/equipped-skin` | `itemId`가 숫자이면 장착, 생략/null/0이면 기본 외형으로 해제한다. 응답에서 `equippedSkin`이 null이면 기본 외형 상태다. |
| 캐릭터 성장/서사 | `GET /api/character/v1/characters/{characterId}/status`, `POST /api/character/v1/characters/{characterId}/interactions` | 상태 조회는 성장 상태를 포함하고, 상호작용은 캐릭터 기억 조각 해금 여부를 반환한다. |
| 별친구 대화 | `POST /api/character/v1/characters/{characterId}/talk/stream` | SSE로 `meta`, `delta`, `done` 이벤트를 반환한다. AI provider 실패 시 캐릭터별 fallback 문장으로 종료한다. |
| 지갑 거래내역 | `GET /api/wallet/v1/wallets/me/transactions` | cursor 기반 최신순 목록이며 `occurredAt`은 거래 생성 시각이다. |
| 상점/보관함 아이템 | `GET /api/item/v1/items`, `GET /api/item/v1/user-items` | 응답에 `characterTypeId`, `effectType`, `imageUrl`이 포함된다. 캐릭터별 스킨 필터와 소모품 UI 매핑은 해당 필드를 기준으로 한다. |
| 아이템 구매 | `POST /api/item/v1/item-purchases` | body의 `idempotencyKey`를 구매 재시도 멱등키로 사용한다. |
| 공유 카드 | `GET /api/share/v1/presigned-url`, `POST /api/share/v1/share-cards` | 프론트가 canvas PNG를 presigned URL로 업로드한 뒤, `imageUrl`을 공유 카드 생성 요청에 전달한다. 백엔드는 업로드 URL을 검증하고 DB에는 object key/shareId를 저장한다. |
| 공유 보상 | `POST /api/share/v1/share-events`, `GET /api/share/v1/share-events/today` | 공유 시도와 일일 보상 여부는 `share_logs` 기준으로 기록한다. 오늘 첫 보상 대상이면 `character_outbox_events`에 `SHARE_REWARD_REQUESTED` 이벤트를 저장한 뒤 커밋 후 user wallet gRPC `EarnStarPiece`를 즉시 호출한다. 성공 시 `wallet.starPiece`에는 적립 후 지갑 잔액을 반환하고, 즉시 지급 실패 시 API는 빠르게 실패하지만 outbox 스케줄러가 재처리한다. |
| 미션 히스토리/상세/피드백 | `GET /api/mission/v1/missions/history`, `GET /api/mission/v1/missions/{missionId}`, `POST /api/mission/v1/missions/{missionId}/feedback` | 목록은 답변 preview와 답변 존재 여부만 반환하고, 상세는 완료 질문/답변 전문을 반환한다. 만족/불만족 피드백은 미션 개인화 신호로 저장한다. |
| 알림 | notification 목록/읽음/구독/설정 API | 푸시 발송 여부는 사용자 알림 설정, 방해금지 시간, FCM 토큰 상태에 따라 결정된다. |

---

## 1. API 전체 요약

| Method | Endpoint                                                        | 설명            | Request | Response | 인증 |
|--------|-----------------------------------------------------------------|---------------|---|---|---|
| GET    | `/api/auth/v1/google/authorization-url`                         | Google OAuth2 시작 URL 조회 | query | OAuth URL | Public |
| POST   | `/api/auth/v1/google/sessions`                                  | Google OAuth2 로그인 세션 생성 | body | token + user | Public |
| POST   | `⚠️ /api/auth/v1/token-refreshes`                               | 토큰 재발급        | body | token | Public |
| DELETE | `/api/auth/v1/sessions/current`                                 | 로그아웃          | none | logout result | 🔐 |
| GET    | `/api/user/v1/users/me`                                         | 내 정보 조회       | none | user | 🔐 |
| GET    | `/api/user/v1/weather-regions`                                  | 선택 가능한 날씨 권역 목록 조회 | none | weather regions | 🔐 |
| GET    | `/api/user/v1/users/me/weather-region`                          | 내 날씨 권역 조회 | none | selected weather region | 🔐 |
| PUT    | `/api/user/v1/users/me/weather-region`                          | 내 날씨 권역 저장/수정 | body | selected weather region | 🔐 |
| GET    | `/api/home/v1/home`                                             | 홈 화면 통합 조회    | none | home data | 🔐 |
| GET    | `💾 /api/character/v1/character-types`                          | 캐릭터 종류 조회     | query | character types | 🔐 |
| GET    | `💾 /api/character/v1/character-types/{characterTypeId}/assets` | 캐릭터 에셋 조회     | path | assets | 🔐 |
| POST   | `/api/character/v1/characters`                                  | 내 캐릭터 생성      | body | character | 🔐 |
| GET    | `/api/character/v1/characters/me`                               | 내 활성 캐릭터 조회   | none | character | 🔐 |
| PATCH  | `/api/character/v1/characters/{characterId}`                    | 캐릭터 이름 수정     | path + body | character | 🔐 |
| GET    | `/api/character/v1/characters/{characterId}/status`             | 캐릭터 상태 조회     | path | status | 🔐 |
| POST   | `⚠️ /api/character/v1/characters/{characterId}/care-logs`       | 돌봄 액션 수행      | path + body | care result | 🔐 |
| PUT    | `⚠️ /api/character/v1/characters/{characterId}/equipped-skin`   | 캐릭터 스킨 장착/해제 | path + body | equipped skin | 🔐 |
| POST   | `/api/character/v1/characters/{characterId}/interactions`       | 캐릭터 터치/상태 기반 상호작용 및 기억 조각 해금 | path + body | interaction result | 🔐 |
| POST   | `/api/character/v1/characters/{characterId}/talk/stream`        | 별친구 대화 SSE 스트리밍 | path + body | SSE stream | 🔐 |
| GET    | `💾 /api/onboarding/v1/questions`                               | 온보딩 질문 목록 조회  | none | questions | 🔐 |
| GET    | `/api/onboarding/v1/profiles/me`                                | 내 온보딩 프로필 조회  | none | profile | 🔐 |
| PUT    | `/api/onboarding/v1/profiles/me`                                | 내 온보딩 프로필 저장/완료 | body | profile | 🔐 |
| GET    | `/api/mission/v1/missions/current`                              | 현재 제안 미션 조회   | query | mission | 🔐 |
| GET    | `/api/mission/v1/missions/today`                                 | 오늘 미션 스택 조회   | none | today missions | 🔐 |
| GET    | `/api/mission/v1/missions/history`                              | 날짜별 미션 스택 조회 | query date | today missions | 🔐 |
| GET    | `/api/mission/v1/missions/{missionId}`                          | 미션 상세 및 완료 답변 조회 | path | mission detail | 🔐 |
| POST   | `/api/mission/v1/missions/today-focus/next`                     | 다음 미션 요청      | body | mission | 🔐 |
| POST   | `/api/mission/v1/missions/{missionId}/rejections`               | 미션 거절 기록 생성   | path + body | rejection | 🔐 |
| POST   | `/api/mission/v1/missions/{missionId}/completion-sessions`      | 완료 질문 세션 시작   | path + body | question | 🔐 |
| POST   | `⚠️ /api/mission/v1/missions/{missionId}/completion-answers`    | 완료 답변 제출 및 보상 지급 | path + body | completion result | 🔐 |
| POST   | `/api/mission/v1/missions/{missionId}/feedback`                 | 미션 만족/불만족 피드백 저장 | path + body | mission feedback | 🔐 |
| GET    | `/api/wallet/v1/wallets/me`                                     | 별조각 잔액 조회     | none | wallet | 🔐 |
| GET    | `/api/wallet/v1/wallets/me/transactions`                        | 별조각 거래내역 조회   | query cursor | transactions | 🔐 |
| GET    | `💾 /api/item/v1/items`                                         | 상점 아이템 목록 조회  | query cursor | items | 🔐 |
| GET    | `/api/item/v1/user-items`                                       | 내 보유 아이템 조회   | query cursor | user items | 🔐 |
| POST   | `⚠️ /api/item/v1/item-purchases`                                | 아이템 구매        | body | purchase result | 🔐 |
| GET    | `/api/share/v1/presigned-url`                                   | 공유 카드 이미지 업로드 URL 발급 | query | presigned url | 🔐 |
| POST   | `/api/share/v1/share-cards`                                     | 공유 카드 이미지 검증 및 생성 | body | share card | 🔐 |
| GET    | `/api/share/v1/share-cards/{shareCardId}`                       | 내 공유 카드 상세 조회 | path | share card detail | 🔐 |
| POST   | `⚠️ /api/share/v1/share-events`                                 | 공유 시도 이벤트 생성 및 보상 처리 | body | share event | 🔐 |
| GET    | `/api/share/v1/share-events/today`                              | 오늘 공유 보상 여부 조회 | none | share status | 🔐 |
| GET    | `💾 /api/share/v1/share-links/{shareId}`                        | 공개 공유 링크 정보 조회 | path | shared card | Public |
| POST   | `/api/share/v1/share-clicks`                                    | 공개 공유 링크 클릭 기록 | body | click result | Public |
| GET    | `/share/{shareId}`                                              | OG 태그 포함 공유 HTML | path | HTML | Public |
| POST   | `⚠️ /api/attendance/v1/attendance-records`                      | 오늘 출석 기록 생성 및 보상 지급 | none | attendance | 🔐 |
| GET    | `/api/attendance/v1/attendance-records`                         | 출석 기록 조회      | query year, month | attendance list | 🔐 |
| GET    | `/api/notification/v1/notifications`                            | 알림 목록 조회      | query cursor | notifications | 🔐 |
| PATCH  | `/api/notification/v1/notifications/{notificationId}`           | 알림 읽음 처리      | path + body | notification | 🔐 |
| POST   | `/api/notification/v1/subscriptions/`                           | FCM 토큰 등록/갱신 | body | subscription | 🔐 |
| GET    | `/api/notification/v1/settings`                                 | 알림 수신 설정 조회 | none | notification settings | 🔐 |
| PATCH  | `/api/notification/v1/settings`                                 | 알림 수신 설정 수정 | body | notification settings | 🔐 |
| 내부 gRPC | `NotificationService.SendPushNotification`                   | 알림 저장 및 FCM 푸시 발송 요청 | proto | success | Internal |
| 내부 gRPC | `NotificationService.GetUnreadNotificationCount`             | 홈 화면 안 읽은 알림 수 조회 | proto | unread count | Internal |

---

## 2. 인증 / 사용자

인증은 Google OAuth2를 기준으로 제공한다. 이메일/비밀번호 로그인 API는 현재 제공하지 않는다.

### 2.1 GET `/api/auth/v1/google/authorization-url`

**설명**  
프론트가 Google 로그인 버튼을 눌렀을 때 이동할 OAuth URL을 받는다.

**Request**

```json
{
  "redirectUri": "https://p5laris.life/oauth/google/callback"
}
```

**Response**

```json
{
  "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?...",
  "state": "oauth-state-token"
}
```

---

### 2.2 POST `/api/auth/v1/google/sessions`

**설명**  
Google OAuth callback에서 받은 `code`를 서버에 전달하고 서비스 토큰을 발급받는다.

**Request**

```json
{
  "code": "google-oauth-code",
  "state": "oauth-state-token",
  "redirectUri": "https://p5laris.life/oauth/google/callback"
}
```

**Response**

```json
{
  "accessToken": "access.jwt",
  "refreshToken": "refresh.jwt",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "별따라걷기",
    "provider": "GOOGLE",
    "role": "USER"
  }
}
```

---

### 2.3 POST `⚠️ /api/auth/v1/token-refreshes`

**설명**  
Refresh Token으로 Access Token을 재발급한다.

**Request**

```json
{
  "refreshToken": "refresh.jwt"
}
```

**Response**

```json
{
  "accessToken": "new-access.jwt",
  "refreshToken": "new-refresh.jwt"
}
```

---

### 2.4 DELETE `/api/auth/v1/sessions/current` 🔐

**설명**  
현재 로그인 세션을 종료한다.

**Request**

```json
{}
```

**Response**

```json
{
  "loggedOut": true
}
```

---

### 2.5 GET `/api/user/v1/users/me` 🔐

**설명**  
현재 로그인한 사용자 정보를 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 1,
  "email": "user@example.com",
  "nickname": "별따라걷기",
  "provider": "GOOGLE",
  "role": "USER",
  "status": "ACTIVE"
}
```

---

### 2.6 GET `/api/user/v1/weather-regions` 🔐

**설명**
사용자가 직접 선택할 수 있는 날씨 권역 목록을 조회한다.

날씨 권역은 미션 생성 시 날씨 기반 context를 구성하는 기준이다. REST 응답에는 화면 표시와 저장에 필요한 코드/이름만 내려주고, 기상청 격자 좌표는 서버 내부에서만 사용한다.

**Request**

```json
{
  "reasonCode": "NOT_NOW",
  "reasonText": "지금은 밖이라서 하기 어려워요."
}
```

거절 이유는 선택 입력이다. 요청 body를 생략하거나 비워 보내면 서버가 `JUST_SKIP` 기준으로 저장한다.

**Response**

```json
{
  "regions": [
    {
      "regionCode": "SEOUL",
      "displayName": "서울"
    },
    {
      "regionCode": "GYEONGGI_SOUTH",
      "displayName": "경기 남부"
    }
  ]
}
```

---

### 2.7 GET `/api/user/v1/users/me/weather-region` 🔐

**설명**
현재 로그인한 사용자의 날씨 권역 선택 상태를 조회한다.

선택한 권역이 없으면 `selected=false`와 `null` 값을 반환한다. 이 경우 mission 서비스는 환경변수로 설정한 기본 권역을 사용한다.

**Request**

```json
{}
```

**Response**

```json
{
  "selected": true,
  "regionCode": "SEOUL",
  "displayName": "서울"
}
```

미선택 상태:

```json
{
  "selected": false,
  "regionCode": null,
  "displayName": null
}
```

---

### 2.8 PUT `/api/user/v1/users/me/weather-region` 🔐

**설명**
현재 로그인한 사용자의 날씨 권역을 저장하거나 수정한다.

`regionCode`는 `GET /api/user/v1/weather-regions`에서 내려준 값만 허용한다. 허용되지 않은 권역 코드는 `INVALID_WEATHER_REGION`으로 응답한다.

**Request**

```json
{
  "regionCode": "GYEONGGI_SOUTH"
}
```

**Response**

```json
{
  "selected": true,
  "regionCode": "GYEONGGI_SOUTH",
  "displayName": "경기 남부"
}
```

---

## 3. 홈

### 3.1 GET `/api/home/v1/home` 🔐

**설명**  
홈 화면에 필요한 사용자, 지갑, 캐릭터, 현재 미션, 알림 요약을 한 번에 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "user": {
    "id": 1,
    "nickname": "별따라걷기"
  },
  "wallet": {
    "starPiece": 120
  },
  "character": {
    "id": 10,
    "name": "작은노바",
    "characterTypeCode": "NOVA",
    "currentAssetUrl": "https://cdn.p5laris.life/characters/nova/idle.png",
    "states": {
      "hunger": { "value": 80, "label": "든든함", "grade": "GOOD" },
      "energy": { "value": 55, "label": "졸림", "grade": "NORMAL" },
      "affection": { "value": 35, "label": "쓸쓸함", "grade": "BAD" }
    }
  },
  "currentMission": {
    "id": 100,
    "title": "물 한 컵 마시기",
    "characterMessage": "물 한 컵 마셔볼래? 나도 빛 좀 마셔볼게.",
    "status": "OFFERED",
    "rewardStarPiece": 5
  },
  "notifications": {
    "unreadCount": 2
  }
}
```

---

## 4. 캐릭터

### 4.1 GET `💾 /api/character/v1/character-types` 🔐

**설명**  
선택 가능한 캐릭터 타입 목록을 조회한다. 현재 캐릭터는 노바, 무무, 쪼리 3종이다.

**Request**

```json
{
  "active": true
}
```

**Response**

```json
{
  "items": [
    {
      "id": 1,
      "code": "NOVA",
      "name": "노바",
      "summary": "자기가 별이었다는 걸 까먹은 별알",
      "sampleLine": "오늘도… 있었네.",
      "sortOrder": 1
    }
  ]
}
```

---

### 4.2 GET `💾 /api/character/v1/character-types/{characterTypeId}/assets` 🔐

**설명**  
캐릭터 타입별 이미지 에셋을 조회한다.

**Request**

```json
{
  "characterTypeId": 1
}
```

**Response**

```json
{
  "characterTypeId": 1,
  "items": [
    {
      "assetType": "IDLE",
      "assetUrl": "https://cdn.p5laris.life/characters/nova/idle.png"
    },
    {
      "assetType": "STATE",
      "assetUrl": "https://cdn.p5laris.life/characters/nova/lonely.png"
    }
  ]
}
```

---

### 4.3 POST `/api/character/v1/characters` 🔐

**설명**  
사용자의 활성 캐릭터를 생성한다. 현재는 사용자당 활성 캐릭터 1개를 기준으로 한다.

**Request**

```json
{
  "characterTypeId": 1,
  "name": "작은노바"
}
```

**Response**

```json
{
  "id": 10,
  "name": "작은노바",
  "characterTypeCode": "NOVA",
  "active": true,
  "states": {
    "hunger": 70,
    "energy": 70,
    "affection": 50
  },
  "createdAt": "2026-05-15T18:00:00+09:00"
}
```

---

### 4.4 GET `/api/character/v1/characters/me` 🔐

**설명**  
내 활성 캐릭터를 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 10,
  "name": "작은노바",
  "characterTypeCode": "NOVA",
  "currentAssetUrl": "https://cdn.p5laris.life/characters/nova/idle.png",
  "assetUrls": {
    "idle": "https://cdn.p5laris.life/characters/nova/idle.png",
    "happy": "https://cdn.p5laris.life/characters/nova/happy.png",
    "sleepy": "https://cdn.p5laris.life/characters/nova/sleepy.png",
    "hungry": "https://cdn.p5laris.life/characters/nova/hungry.png",
    "lowEnergy": "https://cdn.p5laris.life/characters/nova/low-energy.png",
    "lonely": "https://cdn.p5laris.life/characters/nova/lonely.png"
  },
  "active": true,
  "states": {
    "hunger": 80,
    "energy": 55,
    "affection": 35
  },
  "growth": {
    "level": 2,
    "exp": 240,
    "currentLevelExp": 200,
    "nextLevelExp": 600,
    "expToNextLevel": 360,
    "progressPercent": 10,
    "growthStage": "GROWING",
    "growthStageLabel": "자라는 중",
    "maxLevel": false
  },
  "equippedSkin": {
    "itemId": 3,
    "name": "말랑 별빛 스킨"
  }
}
```

`currentAssetUrl`은 서버가 현재 상태값으로 계산한 mood의 이미지 URL이다. `assetUrls`의 키는 `idle`, `happy`, `sleepy`, `hungry`, `lowEnergy`, `lonely`를 사용한다.

---

### 4.5 PATCH `/api/character/v1/characters/{characterId}` 🔐

**설명**  
캐릭터 이름을 수정한다. 이름은 1~10자로 제한한다.

**Request**

```json
{
  "name": "노바별"
}
```

**Response**

```json
{
  "id": 10,
  "name": "노바별",
  "updatedAt": "2026-05-15T18:05:00+09:00"
}
```

---

### 4.6 GET `/api/character/v1/characters/{characterId}/status` 🔐

**설명**  
캐릭터 상태값과 화면 표시용 라벨을 조회한다.

**Request**

```json
{
  "characterId": 10
}
```

**Response**

```json
{
  "characterId": 10,
  "states": {
    "hunger": { "value": 80, "label": "든든함", "grade": "GOOD" },
    "energy": { "value": 55, "label": "졸림", "grade": "NORMAL" },
    "affection": { "value": 35, "label": "쓸쓸함", "grade": "BAD" }
  },
  "growth": {
    "level": 2,
    "exp": 240,
    "currentLevelExp": 200,
    "nextLevelExp": 600,
    "expToNextLevel": 360,
    "progressPercent": 10,
    "growthStage": "GROWING",
    "growthStageLabel": "자라는 중",
    "maxLevel": false
  }
}
```

---

### 4.7 POST `⚠️ /api/character/v1/characters/{characterId}/care-logs` 🔐

**설명**  
밥 주기, 재우기, 놀아주기 같은 돌봄 액션을 수행한다. 현재 클라이언트는 돌봄 액션에 맞는 소모품 `itemId`를 전달하며, 백엔드는 `user_items.quantity`를 1개 차감한다.
중복 요청 및 재시도를 방지하기 위해 반드시 `Idempotency-Key` 헤더를 함께 전송해야 합니다.

| actionType | 필요한 effectType | 예시 소모품 |
|---|---|---|
| `FEED` | `FOOD` | 별사탕밥 |
| `SLEEP` | `REST` | 구름 베개 |
| `PLAY` | `PLAY` | 별 장난감 |

**Headers**

| Name | Type | Description | Required |
|---|---|---|---|
| `Idempotency-Key` | String | 중복 요청 및 재시도 방지용 고유 멱등키 (예: UUID 또는 고유 문자열) | Yes |

**Request**

```json
{
  "actionType": "FEED",
  "itemId": 21
}
```

**Response**

```json
{
  "careLogId": 300,
  "characterId": 10,
  "actionType": "FEED",
  "consumed": {
    "itemId": 21,
    "quantity": 1
  },
  "beforeStates": {
    "hunger": 50,
    "energy": 55,
    "affection": 35
  },
  "afterStates": {
    "hunger": 80,
    "energy": 55,
    "affection": 35
  },
  "beforeGrowth": {
    "level": 1,
    "exp": 195,
    "currentLevelExp": 0,
    "nextLevelExp": 200,
    "expToNextLevel": 5,
    "progressPercent": 97,
    "growthStage": "BABY",
    "growthStageLabel": "새싹",
    "maxLevel": false
  },
  "afterGrowth": {
    "level": 2,
    "exp": 200,
    "currentLevelExp": 200,
    "nextLevelExp": 600,
    "expToNextLevel": 400,
    "progressPercent": 0,
    "growthStage": "GROWING",
    "growthStageLabel": "자라는 중",
    "maxLevel": false
  },
  "expGained": 5,
  "levelUp": true,
  "characterMessage": "먹는 중… 빛도 맛이 있구나."
}
```

---

### 4.8 PUT `⚠️ /api/character/v1/characters/{characterId}/equipped-skin` 🔐

**설명**  
캐릭터에 스킨을 장착하거나 기본 외형으로 해제한다. 스킨은 한 번에 하나만 적용한다.

- `itemId`가 숫자이면 해당 보유 스킨을 장착한다.
- `itemId`가 생략되거나 `null`이면 현재 장착 스킨을 해제하고 기본 외형으로 되돌린다.
- 클라이언트는 `GET /api/character/v1/characters/me` 응답의 `equippedSkin.itemId`와 `GET /api/item/v1/user-items` 응답의 `itemId`를 비교해 장착 여부를 표시한다.

**Request**

장착:

```json
{
  "itemId": 3
}
```

해제:

```json
{
  "itemId": null
}
```

**Response**

장착:

```json
{
  "characterId": 10,
  "equippedSkin": {
    "itemId": 3,
    "name": "말랑 별빛 스킨"
  },
  "updatedAt": "2026-05-15T18:40:00+09:00"
}
```

해제:

```json
{
  "characterId": 10,
  "equippedSkin": null,
  "updatedAt": "2026-05-15T18:45:00+09:00"
}
```

---

### 4.9 POST `/api/character/v1/characters/{characterId}/interactions` 🔐

**설명**
캐릭터를 터치하거나 상태/시간대 기반 상호작용을 요청한다. 응답은 화면에 바로 표시할 캐릭터 대사와 기억 조각 해금 여부를 포함한다.

`interactionType`을 비우면 서버는 `TAP`으로 처리한다.

| interactionType | 설명 |
|---|---|
| `TAP` | 기본 터치 |
| `LEVEL_UP` | 레벨업 반응 |
| `LOW_HUNGER` | 포만감 BAD 상태 반응 |
| `LOW_ENERGY` | 기운 BAD 상태 반응 |
| `LOW_AFFECTION` | 애정 BAD 상태 반응 |
| `NIGHT` | 밤 시간대 반응 |
| `MIDNIGHT` | 자정 이후 반응 |

**Request**

```json
{
  "interactionType": "TAP"
}
```

**Response**

```json
{
  "characterId": 10,
  "characterTypeCode": "MUMU",
  "level": 2,
  "fragmentType": "LORE",
  "triggerType": "TAP",
  "message": "무... 무무.",
  "interpretation": "무무가 잎맥 속 오래된 기록을 조금씩 되찾는 것 같아요.",
  "memoryUnlocked": true,
  "alreadyUnlocked": false,
  "memory": {
    "memoryKey": "mumu_lv2_lore_001",
    "title": "잎맥의 문장",
    "storyText": "무무의 잎맥이 조금 더 선명해졌어요..."
  }
}
```

`fragmentType=COMMON`이거나 새로 해금된 기억 조각이 없으면 `memory`는 `null`일 수 있다.

---

### 4.10 POST `/api/character/v1/characters/{characterId}/talk/stream` 🔐

**설명**
별친구에게 말을 걸고 SSE(Server-Sent Events)로 응답을 스트리밍한다. gateway는 character 모듈에서 캐릭터 상태/성장/해금 기억 context를 조회하고, ai 모듈의 `StreamCharacterTalk` gRPC를 호출한다.

대화 원문은 DB에 저장하지 않는다.

**Request**

```json
{
  "message": "나 오늘 너무 힘들었어",
  "interactionType": "TAP"
}
```

**SSE Events**

`meta`

```json
{
  "requestId": "e0694194-17a6-4a19-bbb8-eac5b1655e2e",
  "characterId": 10,
  "characterTypeCode": "MUMU",
  "level": 2,
  "sentAt": "2026-06-04T01:20:00Z"
}
```

`delta`

```json
{
  "text": "무... 무무. "
}
```

`done`

```json
{
  "requestId": "e0694194-17a6-4a19-bbb8-eac5b1655e2e",
  "fallbackUsed": false
}
```

fallback이 사용된 경우:

```json
{
  "requestId": "e0694194-17a6-4a19-bbb8-eac5b1655e2e",
  "fallbackUsed": true,
  "errorType": "AI_ERROR_TYPE_PROVIDER_ERROR"
}
```

**처리 규칙**

- `delta` 이벤트는 여러 번 올 수 있으며, 프론트는 순서대로 이어 붙여 최종 문장을 만든다.
- AI provider 오류, timeout, 출력 검증 실패가 발생하면 gateway 또는 ai 모듈에서 캐릭터별 fallback 문장을 내려준다.
- 무무 응답은 무무 말투와 해석을 함께 포함한다.
- gateway의 SSE 연결 timeout은 `GATEWAY_CHARACTER_TALK_SSE_TIMEOUT_MS`, AI gRPC deadline은 `GATEWAY_CHARACTER_TALK_AI_DEADLINE_MS`, prompt에 넣는 기억 조각 수는 `GATEWAY_CHARACTER_TALK_MEMORY_LIMIT`로 조절한다.

---

## 5. 온보딩

### 5.1 GET `💾 /api/onboarding/v1/questions` 🔐

**설명**  
온보딩 고정 질문 목록을 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "items": [
    {
      "key": "ROUTINE_GOAL",
      "content": "지금 만들고 싶은 루틴은 무엇인가요?",
      "multipleSelection": true,
      "maxSelectionCount": 3,
      "options": [
        { "key": "HYDRATION_MEAL", "value": "물/식사 챙기기" },
        { "key": "SPACE_RESET", "value": "공간 가볍게 정리하기" },
        { "key": "EXERCISE_HABIT", "value": "운동 습관 만들기" }
      ]
    }
  ]
}
```

---

### 5.2 GET `/api/onboarding/v1/profiles/me` 🔐

**설명**  
내 온보딩 프로필과 완료 여부를 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "completed": true,
  "livingType": null,
  "wakeUpTime": null,
  "sleepTime": null,
  "preferredMissionTime": null,
  "routineGoal": null,
  "activityPreference": null,
  "missionIntensity": "LIGHT",
  "answersJson": "{\"onboardingVersion\":2}",
  "onboardingVersion": 2,
  "routineGoals": ["HYDRATION_MEAL", "SPACE_RESET"],
  "preferredTimeSlots": ["EVENING", "NIGHT"],
  "missionPlaceContexts": ["HOME"],
  "avoidedMissionTags": ["OUTDOOR"]
}
```

---

### 5.3 PUT `/api/onboarding/v1/profiles/me` 🔐

**설명**  
온보딩 답변을 저장한다. `completed=true`이면 미션 기능 진입이 가능해진다.

**Request**

```json
{
  "livingType": null,
  "wakeUpTime": null,
  "sleepTime": null,
  "preferredMissionTime": null,
  "routineGoal": null,
  "activityPreference": null,
  "missionIntensity": "LIGHT",
  "answersJson": "{\"onboardingVersion\":2}",
  "onboardingVersion": 2,
  "routineGoals": ["HYDRATION_MEAL", "SPACE_RESET"],
  "preferredTimeSlots": ["EVENING", "NIGHT"],
  "missionPlaceContexts": ["HOME"],
  "avoidedMissionTags": ["OUTDOOR"],
  "completed": true
}
```

**Response**

```json
{
  "completed": true,
  "missionAvailable": true,
  "completedAt": "2026-05-15T18:10:00+09:00"
}
```

---

## 6. 미션

미션은 사용자가 직접 목록에서 고르는 구조가 아니라, 서버가 현재 미션 1개를 제안하는 구조다. 오늘 제안된 미션은 stack으로 저장된다.

미션 조회와 상태 변경의 소유권 기준은 `characterId`가 아니라 로그인한 `userId`다. `characterId`는 "어떤 캐릭터가 이 미션을 제안했는지"를 남기는 기록용 값으로 사용한다.

현재 정책:

```text
한 유저는 하루에 OFFERED/ANSWERING 상태 미션을 동시에 1개만 가진다.
하루 미션 완료 보상은 최대 20회까지 가능하다.
하루 미션 거절은 최대 10회까지 가능하다.
CHALLENGE 난이도 미션은 하루 1회까지만 제안한다.
난이도별 기본 보상은 EASY 10, NORMAL 15, CHALLENGE 30 별조각이다.
미션 완료는 완료 버튼 클릭 후 완료 질문 1개에 텍스트 답변을 제출해야 처리된다.
완료 답변은 1자 이상 300자 이하로 입력한다.
```

입력 검증:

```text
missionId path variable은 1 이상의 숫자여야 한다.
다음 미션 요청의 characterId는 필수이며 1 이상의 숫자여야 한다.
다음 미션 요청의 lastMissionId는 선택값이며, 전달하는 경우 0 이상의 숫자여야 한다.
처음 미션 요청처럼 직전 미션이 없으면 lastMissionId는 생략하거나 null로 보낼 수 있다.
완료 답변 answer는 공백만으로 구성될 수 없고 300자를 초과할 수 없다.
피드백 reasonText는 100자를 초과할 수 없다.
입력값 형식 오류, JSON body 누락/파싱 오류, path variable 타입 오류는 INVALID_INPUT_VALUE로 응답한다.
```

### 6.1 GET `/api/mission/v1/missions/current` 🔐

**설명**
오늘 기준 로그인한 유저의 현재 미션을 조회한다. `OFFERED` 또는 `ANSWERING` 상태의 미션이 현재 미션이다.

`characterId`는 조회 조건으로 사용하지 않는다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 100,
  "missionDate": "2026-05-20",
  "stackOrder": 2,
  "title": "물 한 컵 마시기",
  "description": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
  "characterMessage": "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "category": "BASIC_ROUTINE",
  "difficulty": "EASY",
  "rewardStarPiece": 10,
  "status": "OFFERED"
}
```

---

### 6.2 GET `/api/mission/v1/missions/today` 🔐

**설명**
오늘 기준 로그인한 유저에게 제안된 미션 스택을 조회한다. 하루 미션 스택은 사용자별·일자별로 제한된 작은 목록이므로 pagination은 제공하지 않는다.

목록은 `stackOrder` 오름차순으로 반환한다. `currentMissionId`는 현재 진행 중인 `OFFERED` 또는 `ANSWERING` 상태 미션의 id이며, 현재 미션이 없으면 `null`이다.

`offeredCount`는 현재 `OFFERED` 상태인 미션 수가 아니라, 오늘 유저에게 제안된 전체 미션 stack 개수다. 따라서 `remainingOfferCount`는 `maxDailyOffers - offeredCount`로 계산한다.

mission REST 응답에는 AI fallback 여부를 노출하지 않는다. fallback 여부는 `ai_mission_generations`, `ai_usage_logs`, event-log의 `AI_FALLBACK_USED`에서 운영 분석용으로 추적한다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionDate": "2026-05-26",
  "maxDailyOffers": 20,
  "offeredCount": 4,
  "completedCount": 2,
  "rejectedCount": 1,
  "remainingOfferCount": 16,
  "maxDailyRewardCount": 20,
  "completedRewardCount": 2,
  "remainingRewardCount": 18,
  "maxDailyRejectCount": 10,
  "remainingRejectCount": 9,
  "currentMissionId": 104,
  "missions": [
    {
      "id": 101,
      "stackOrder": 1,
      "title": "물 한 컵 마시기",
      "category": "BASIC_ROUTINE",
      "difficulty": "EASY",
      "rewardStarPiece": 10,
      "status": "COMPLETED",
      "characterMessage": "잘했어. 작은 시작도 별조각이 됐어.",
      "createdAt": "2026-05-26T09:10:00+09:00",
      "completedAt": "2026-05-26T09:15:00+09:00",
      "rejectedAt": null,
      "completionQuestion": "물 마시고 나서 기분이 조금 달라졌어?",
      "answerPreview": "조금 시원해졌어.",
      "hasAnswer": true
    },
    {
      "id": 104,
      "stackOrder": 4,
      "title": "창문 열고 숨 고르기",
      "category": "BASIC_ROUTINE",
      "difficulty": "EASY",
      "rewardStarPiece": 10,
      "status": "OFFERED",
      "characterMessage": "무... 오늘의 작은 별을 찾은 것 같아요.",
      "createdAt": "2026-05-26T11:30:00+09:00",
      "completedAt": null,
      "rejectedAt": null,
      "completionQuestion": null,
      "answerPreview": null,
      "hasAnswer": false
    }
  ]
}
```

---

### 6.2.1 GET `/api/mission/v1/missions/history?date=YYYY-MM-DD` 🔐

**설명**
특정 날짜 기준으로 로그인한 유저에게 제안된 미션 스택을 조회한다.

응답 구조는 `GET /api/mission/v1/missions/today`와 동일하다. 목록 화면에서는 답변 전문을 내려주지 않고 `answerPreview`, `hasAnswer`만 제공한다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionDate": "2026-05-25",
  "maxDailyOffers": 20,
  "offeredCount": 3,
  "completedCount": 2,
  "rejectedCount": 1,
  "remainingOfferCount": 17,
  "maxDailyRewardCount": 20,
  "completedRewardCount": 2,
  "remainingRewardCount": 18,
  "maxDailyRejectCount": 10,
  "remainingRejectCount": 9,
  "currentMissionId": null,
  "missions": [
    {
      "id": 98,
      "stackOrder": 1,
      "title": "어깨 힘 빼고 숨 세 번 쉬기",
      "category": "MINI_EXERCISE",
      "difficulty": "EASY",
      "rewardStarPiece": 10,
      "status": "COMPLETED",
      "characterMessage": "조금만 풀어도 몸이 별빛처럼 느슨해질 거예요.",
      "createdAt": "2026-05-25T21:10:00+09:00",
      "completedAt": "2026-05-25T21:12:00+09:00",
      "rejectedAt": null,
      "completionQuestion": "숨을 쉬고 나니 어깨가 조금 달라졌나요?",
      "answerPreview": "조금 가벼워졌어.",
      "hasAnswer": true
    }
  ]
}
```

---

### 6.2.2 GET `/api/mission/v1/missions/{missionId}` 🔐

**설명**
미션 1개의 상세 정보와 완료 질문/답변 전문을 조회한다.

소유권은 `userId + missionId` 기준으로 확인한다. 완료 답변이 저장된 미션이면 `answer`에 전문을 반환하고, 답변 전 상태이면 `answer=null`, `hasAnswer=false`로 반환한다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 98,
  "missionDate": "2026-05-25",
  "stackOrder": 1,
  "title": "어깨 힘 빼고 숨 세 번 쉬기",
  "description": "자리에서 어깨를 내리고 숨을 세 번 천천히 쉬어보세요.",
  "characterMessage": "조금만 풀어도 몸이 별빛처럼 느슨해질 거예요.",
  "category": "MINI_EXERCISE",
  "difficulty": "EASY",
  "rewardStarPiece": 10,
  "status": "COMPLETED",
  "createdAt": "2026-05-25T21:10:00+09:00",
  "completedAt": "2026-05-25T21:12:00+09:00",
  "rejectedAt": null,
  "question": "숨을 쉬고 나니 어깨가 조금 달라졌나요?",
  "answer": "조금 가벼워졌어.",
  "completionCharacterResponse": "좋아요. 그 가벼움을 오늘 별조각으로 기억할게요.",
  "hasAnswer": true
}
```

---

### 6.3 POST `/api/mission/v1/missions/today-focus/next` 🔐

**설명**
다음 미션을 생성해 현재 미션으로 제안한다.

진행 중인 미션(`OFFERED`, `ANSWERING`)이 이미 있으면 새 미션을 만들 수 없다. 현재 미션을 거절하거나 완료한 뒤 다시 호출해야 한다.

`characterId`는 제안 캐릭터 기록용이며, 현재 미션 중복 판단은 `userId` 기준으로 수행한다.

**Request**

```json
{
  "characterId": 10,
  "lastMissionId": 101
}
```

`characterId`는 미션 제안 캐릭터를 기록하기 위해 전달한다. `lastMissionId`는 클라이언트가 마지막으로 보고 있던 미션을 함께 보내기 위한 필드다. 첫 미션 요청처럼 직전 미션이 없으면 `lastMissionId`는 생략하거나 `null`로 보낼 수 있다. 진행 중 미션 존재 여부는 서버 상태 기준으로 확인한다.

**Response**

```json
{
  "id": 101,
  "missionDate": "2026-05-20",
  "stackOrder": 3,
  "title": "책상 위 물건 하나 치우기",
  "description": "책상 위에서 물건 하나만 제자리로 옮겨보세요.",
  "characterMessage": "물건 하나만 치워도 공간이 조금 숨을 쉴 수 있어.",
  "category": "SPACE_RESET",
  "difficulty": "EASY",
  "rewardStarPiece": 10,
  "status": "OFFERED"
}
```

---

### 6.4 POST `/api/mission/v1/missions/{missionId}/rejections` 🔐

**설명**
현재 제안된 미션을 거절한다. 거절은 실패가 아니며, 별조각 차감도 없다.

거절 권한 확인은 `userId + missionId` 기준으로 수행한다. `characterId`는 소유권 조건으로 사용하지 않는다.

거절 후 다음 미션은 이 API에서 자동 생성하지 않는다. 클라이언트는 필요하면 `POST /api/mission/v1/missions/today-focus/next`를 다시 호출한다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionId": 101,
  "status": "REJECTED",
  "rejectedAt": "2026-05-20T18:30:00+09:00",
  "characterMessage": "괜찮아요. 다른 별을 찾아볼게요."
}
```

---

### 6.5 POST `/api/mission/v1/missions/{missionId}/completion-sessions` 🔐

**설명**
사용자가 완료 버튼을 눌렀을 때 완료 질문 1개를 시작한다. 이 시점에는 아직 보상을 지급하지 않는다.

`OFFERED` 상태 미션은 `ANSWERING` 상태로 전환된다. 이미 `ANSWERING` 상태이면 기존 질문을 다시 반환하므로, 모바일 중복 클릭이나 네트워크 재시도에도 같은 질문 세션을 유지한다.

**Request**

```json
{}
```

**Response**

```json
{
  "missionId": 101,
  "status": "ANSWERING",
  "question": {
    "id": 501,
    "text": "어떤 물건을 치웠어?",
    "inputType": "TEXT",
    "minLength": 1,
    "maxLength": 300
  }
}
```

---

### 6.6 POST `⚠️ /api/mission/v1/missions/{missionId}/completion-answers` 🔐

**설명**
완료 질문에 답변한다. 답변 저장 후 미션을 `COMPLETED`로 전환한다.

완료 답변은 1자 이상 300자 이하로 입력한다. 답변 전문은 `mission_completion_answers`에 저장하고, event-log에는 답변 전문을 남기지 않는다.

미션 완료 보상은 1회만 지급된다. 응답의 `reward`는 지급된 보상량을, `wallet`은 보상 반영 후 지갑 스냅샷을 의미한다.
별조각 보상 지급은 `mission_outbox_events`에 `MISSION_REWARD_REQUESTED` 이벤트로 기록한 뒤 wallet 모듈에 전달한다. 같은 미션 완료 요청이 재시도되면 저장된 답변과 outbox 멱등키를 기준으로 처리한다.

**Request**

```json
{
  "answer": "책상 위에 있던 컵을 싱크대에 가져다 놨어."
}
```

**Response**

```json
{
  "missionId": 101,
  "status": "COMPLETED",
  "answer": {
    "text": "책상 위에 있던 컵을 싱크대에 가져다 놨어.",
    "answeredAt": "2026-05-20T18:35:00+09:00"
  },
  "reward": {
    "starPiece": 10,
    "affection": 0
  },
  "wallet": {
    "starPiece": 127
  },
  "characterMessage": "작은 정리도 오늘의 별조각으로 남겨둘게."
}
```

---

### 6.6.1 POST `/api/mission/v1/missions/{missionId}/feedback` 🔐

**설명**
미션에 대한 만족/불만족 피드백을 저장한다.

피드백은 보상 지급 조건이 아니며, 개인화 후보 생성과 회피 신호 분석에 사용한다. 같은 사용자·미션·피드백 타입으로 다시 요청하면 기존 피드백을 갱신한다.

**Request**

```json
{
  "feedbackType": "SATISFACTION",
  "reaction": "LIKE",
  "reasonCode": "NOT_INTERESTED",
  "reasonText": "운동 미션은 좋지만 조금 더 짧으면 좋겠어요."
}
```

**Response**

```json
{
  "missionId": 101,
  "feedbackType": "SATISFACTION",
  "reaction": "LIKE",
  "reasonCode": "NOT_INTERESTED",
  "reasonText": "운동 미션은 좋지만 조금 더 짧으면 좋겠어요.",
  "updatedAt": "2026-05-20T18:40:00+09:00"
}
```

---

### 6.7 내부 gRPC `AiService.GenerateMissionTexts`

**설명**
미션 후보 생성과 캐릭터 말투 문구 생성을 수행한다. 외부 클라이언트가 직접 호출하는 REST API가 아니라, mission 모듈이 내부에서 사용하는 AI gRPC API다.

AI는 온보딩, 최근 미션/피드백, 시간대, 날씨 context를 바탕으로 아래 값을 구조화 응답으로 반환한다. mission 모듈은 응답을 저장하기 전에 카테고리/난이도/보상/문장 길이/금지 표현/CHALLENGE 일일 제한을 검증한다.

```text
미션 제목 title
미션 설명 description
미션 카테고리 category
미션 난이도 difficulty
제안 문구 characterMessage
완료 질문 completionQuestion
완료 후 캐릭터 반응 completionCharacterResponse
```

외부 provider 오류, 응답 구조 오류, 정책 위반, rate limit 초과 등으로 생성 결과를 사용할 수 없으면 seed 미션 템플릿과 fallback 문구를 사용한다.

`requestId`는 AI 생성 요청의 멱등 기준이다. 같은 `requestId`와 같은 요청 본문이 다시 들어오면 기존 `ai_mission_generations` 결과를 반환하고, 같은 `requestId`가 다른 요청 본문과 함께 들어오면 충돌로 처리한다. mission 모듈은 매 호출마다 랜덤 UUID를 만들지 않고, 같은 미션 문구 생성 시도에 같은 `requestId`를 사용한다.

외부 AI provider를 사용하는 경우 ai 모듈은 `requestId` 멱등 결과를 먼저 확인한 뒤 Redis 기반 rate limit을 확인한다. rate limit 초과 또는 Redis rate limit 저장소 장애가 발생하면 외부 provider를 호출하지 않고 fallback 결과를 반환한다.

**gRPC Request**

```json
{
  "userId": 1,
  "characterId": 10,
  "characterType": "NOVA",
  "missionTemplateId": 12,
  "baseTitle": "물 한 컵 마시기",
  "baseDescription": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
  "category": "BASIC_ROUTINE",
  "difficulty": "EASY",
  "fallbackCharacterMessage": "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "fallbackQuestion": "물 마시고 나서 기분이 조금 달라졌어?",
  "fallbackCompletionResponse": "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
  "onboardingContextJson": "{\"routineGoal\":\"SELF_CARE\"}",
  "recentMissionContextJson": "{\"recentRejected\":[]}",
  "requestId": "MISSION_TEXT:2f3a4b..."
}
```

**gRPC Response**

```json
{
  "aiGenerationId": 55,
  "status": "SUCCESS",
  "title": "물 한 컵으로 작은 리셋 만들기",
  "description": "자리에서 물 한 컵을 천천히 마시고 몸이 깨어나는 느낌을 살펴보세요.",
  "category": "BASIC_ROUTINE",
  "difficulty": "EASY",
  "characterMessage": "천천히 물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "completionQuestion": "물 마시고 나서 기분이 조금 달라졌어?",
  "completionCharacterResponse": "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
  "fallbackUsed": false,
  "requestId": "MISSION_TEXT:2f3a4b..."
}
```

fallback 응답 예시:

```json
{
  "aiGenerationId": 56,
  "status": "FALLBACK",
  "title": "물 한 컵 마시기",
  "description": "지금 자리에서 물 한 컵을 천천히 마셔보세요.",
  "category": "BASIC_ROUTINE",
  "difficulty": "EASY",
  "characterMessage": "물 한 컵 마셔볼래? 작은 시작도 별조각이 될 수 있어.",
  "completionQuestion": "물 마시고 나서 기분이 조금 달라졌어?",
  "completionCharacterResponse": "잘했어. 오늘의 작은 수분 보충을 별조각으로 기억할게.",
  "fallbackUsed": true,
  "errorType": "INVALID_OUTPUT",
  "requestId": "MISSION_TEXT:2f3a4b..."
}
```

AI 생성 결과는 `ai_mission_generations`, 사용 로그는 `ai_usage_logs`에 저장한다. `ai_mission_generations.request_id`는 생성 결과 재사용 기준이고, `ai_usage_logs.request_id`는 해당 생성 시도의 사용량/지연 추적 기준이다. fallback이 사용되면 event-log에 `AI_FALLBACK_USED`를 남긴다. mission REST 응답에는 `fallbackUsed`를 노출하지 않고, 운영 분석은 저장된 생성 결과와 로그를 기준으로 한다.

---

### 6.8 내부 gRPC `AiService.GenerateTextEmbedding`

**설명**
미션 완료 답변과 피드백에서 추출한 사용자 기억을 RAG 검색에 사용할 embedding vector로 변환한다.

mission 모듈은 `user_memories`를 먼저 저장하고, embedding이 필요한 기억은 `user_memory_embeddings`에 작업 상태를 남긴다. ai 모듈은 요청받은 텍스트를 `gemini-embedding-001` 기준 768차원 vector로 변환해 반환한다.

**gRPC Request**

```json
{
  "userId": 1,
  "text": "밤에는 밖에 나가기보다 실내에서 할 수 있는 짧은 미션을 선호한다.",
  "model": "gemini-embedding-001",
  "dimension": 768,
  "requestId": "USER_MEMORY_EMBEDDING:1:55"
}
```

**gRPC Response**

```json
{
  "model": "gemini-embedding-001",
  "dimension": 768,
  "values": [0.0123, -0.0045, 0.0187],
  "requestId": "USER_MEMORY_EMBEDDING:1:55"
}
```

응답 예시의 `values`는 축약 표기다. 실제 응답은 768개 float 값을 가진다.

---

### 6.9 내부 gRPC `AiService.StreamCharacterTalk`

**설명**
별친구 대화 응답을 provider streaming으로 생성한다. gateway는 REST SSE 계약을 유지하고, ai 모듈은 gRPC server streaming으로 `DELTA`, `DONE`, `ERROR` 이벤트를 반환한다.

AI는 Spring AI Tool Calling으로 필요한 백엔드 context를 조회한다. Tool 호출 실패는 대화 전체 실패가 아니라 해당 context를 사용할 수 없는 상태로 prompt에 반영한다.

현재 Tool 범위:

| Tool | 용도 |
|---|---|
| 캐릭터 상태/성장/기억 조회 | 캐릭터 상태, 성장 레벨, 최근 해금 기억 조각을 확인한다. |
| 오늘 미션 조회 | 오늘 현재 미션과 미션 진행 상태를 확인한다. |
| 최근 루틴 요약 조회 | 최근 미션, 답변, 만족도/거절 피드백 흐름을 확인한다. |
| 시간대/날씨 context 조회 | 현재 시간대, 사용자 날씨 권역, 날씨 기반 미션 정책을 확인한다. |

**gRPC Request**

```json
{
  "userId": 1,
  "characterId": 10,
  "characterType": "MUMU",
  "characterName": "무무",
  "userMessage": "나 오늘 너무 힘들었어",
  "interactionType": "TAP",
  "characterContextJson": "{\"growth\":{\"level\":2},\"memories\":[...]}",
  "requestId": "CHARACTER_TALK:..."
}
```

**gRPC Stream Response**

```json
{ "eventType": "CHARACTER_TALK_STREAM_EVENT_TYPE_DELTA", "text": "무... 무무.", "requestId": "CHARACTER_TALK:..." }
{ "eventType": "CHARACTER_TALK_STREAM_EVENT_TYPE_DELTA", "text": " (해석: 무무가", "requestId": "CHARACTER_TALK:..." }
{ "eventType": "CHARACTER_TALK_STREAM_EVENT_TYPE_DONE", "fallbackUsed": false, "requestId": "CHARACTER_TALK:..." }
```

오류 또는 검증 실패 시:

```json
{
  "eventType": "CHARACTER_TALK_STREAM_EVENT_TYPE_ERROR",
  "fallbackUsed": true,
  "errorType": "AI_ERROR_TYPE_PROVIDER_ERROR",
  "requestId": "CHARACTER_TALK:..."
}
```

---

## 7. 별조각 지갑

### 7.1 GET `/api/wallet/v1/wallets/me` 🔐

**설명**  
현재 별조각 잔액을 조회한다.

**Request**

```json
{}
```

**Response**

```json
{
  "starPiece": 127
}
```

---

### 7.2 GET `/api/wallet/v1/wallets/me/transactions` 🔐

**설명**
내 별조각 획득/사용 거래내역을 최신순으로 조회한다. 별조각 화면에서 잔액과 함께 노출한다.

**Request (Query Parameters)**

```json
{
  "cursor": null,
  "size": 20
}
```

**Response**

```json
{
  "items": [
    {
      "id": 1203,
      "amount": 10,
      "balanceAfter": 240,
      "reason": "ATTENDANCE_REWARD",
      "description": "오늘 출석 보상",
      "sourceType": "ATTENDANCE",
      "sourceId": 302,
      "occurredAt": "2026-05-26T10:50:00+09:00"
    },
    {
      "id": 1204,
      "amount": -60,
      "balanceAfter": 180,
      "reason": "ITEM_PURCHASE",
      "description": "말랑 별빛 스킨 구매",
      "sourceType": "ITEM",
      "sourceId": 3,
      "occurredAt": "2026-05-26T11:20:00+09:00"
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasNext": false,
    "size": 20
  }
}
```

---

## 8. 아이템 / 상점

### 8.1 GET `💾 /api/item/v1/items` 🔐

**설명**  
판매 중인 아이템 목록을 조회한다. 가격은 현금이 아니라 별조각 가격이다.
- **모바일 스크롤 UI 대응**: 페이지 누락이나 중복 노출을 방지하기 위해 **커서(Cursor) 기반 페이징**을 적용합니다.
- **탭 필터링**: 화면에 탭(`SKIN`, `CONSUMABLE`) 별로 구분하여 보여주기 위해 쿼리 파라미터 `itemType` 필터링을 제공합니다.
- **캐릭터별 스킨**: `characterTypeId`가 있으면 해당 캐릭터 전용 스킨이며, `null`이면 공용 스킨입니다. 클라이언트는 현재 캐릭터 타입과 비교해 노출 여부를 판단합니다.

**Request (Query Parameters)**

```json
{
  "itemType": "SKIN", 
  "active": true,     
  "cursor": null,    
  "size": 20         
}
```

**Response**

```json
{
  "items": [
    {
      "id": 3,
      "name": "말랑 별빛 스킨",
      "description": "말랑말랑하게 빛나는 스킨입니다.",
      "itemType": "SKIN",
      "characterTypeId": 2,
      "effectType": null,
      "price": 60,
      "imageUrl": "https://cdn.p5laris.life/items/skin-soft-star.png",
      "owned": false 
    }
  ],
  "pageInfo": {
    "nextCursor": "eyJpZCI6M30=",
    "hasNext": false,
    "size": 20
  }
}
```

---

### 8.2 GET `/api/item/v1/user-items` 🔐

**설명**  
내가 보유한 아이템과 수량을 조회한다.

- **소모성 아이템**: 보유 개수(`quantity`)가 표현됩니다.
- **장착형 아이템(스킨)**: 장착 여부는 이 API에 포함되지 않습니다. 클라이언트가 보유한 캐릭터 정보(`equipped_skin_id`)와 비교하여 장착 상태를 판단합니다.
- **돌봄 사용**: `itemType=CONSUMABLE`로 조회한 뒤 `effectType`이 돌봄 액션과 맞는 아이템의 `itemId`를 `POST /api/character/v1/characters/{characterId}/care-logs`에 전달합니다.

> **장착 여부 판단 방식 (클라이언트 싱크)**  
> `GET /api/character/v1/characters/me` 응답의 `equippedSkin.itemId` 와  
> 이 API 응답의 `itemId` 를 클라이언트에서 비교합니다.  
> `itemId === equippedSkin.itemId` → 장착된 스킨
> `equippedSkin === null` → 기본 외형 상태
> 스킨 노출 범위는 `characterTypeId`로 판단하며, 현재 캐릭터 타입과 일치하거나 `null`인 공용 스킨만 보여줍니다.

**Request (Query Parameters)**

```json
{
  "itemType": "CONSUMABLE", 
  "cursor": null,
  "size": 20
}
```

**Response**

```json
{
  "items": [
    {
      "userItemId": 40,
      "itemId": 5,
      "name": "별 장난감",
      "itemType": "CONSUMABLE",
      "characterTypeId": null,
      "effectType": "PLAY",
      "quantity": 3,
      "imageUrl": "https://cdn.p5laris.life/items/star-toy.png"
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasNext": false,
    "size": 20
  }
}
```

---

### 8.3 POST `⚠️ /api/item/v1/item-purchases` 🔐

**설명**  
별조각으로 아이템을 구매한다.
- **스킨 아이템 구매 정책**: 유저당 최대 **1개**만 구매 가능합니다. 이미 보유 중인 경우 `ITEM_ALREADY_OWNED` (400) 에러가 발생합니다.
- **소모성 아이템 구매 정책**: 수량(`quantity`)을 선택하여 다량 구매가 가능하며, 구매 시 기존 보유량에 누적됩니다.
- **트랜잭션**: 별조각 차감, 거래 내역 기록(`star_piece_transactions`), 보유 아이템(`user_items`) 추가/업데이트는 단일 트랜잭션으로 처리되어 정합성을 유지합니다.
- **재화 부족**: 지갑 잔액이 부족하면 `STAR_PIECE_NOT_ENOUGH` (400) 에러가 발생합니다.
클라이언트는 구매 버튼 1회 시도마다 고유한 `idempotencyKey`를 생성하고, 네트워크 재시도에는 같은 값을 재사용한다.

**Request**

```json
{
  "itemId": 3,
  "quantity": 1,
  "idempotencyKey": "item-purchase-20260526-uuid"
}
```

**Response**

```json
{
  "purchaseId": 700,
  "itemId": 3,
  "name": "말랑 별빛 스킨",
  "quantity": 1,
  "price": 60,
  "wallet": {
    "starPiece": 67
  },
  "transactionId": 901
}
```

---

## 9. 공유

### 9.1 GET `/api/share/v1/presigned-url` 🔐

**설명**  
프론트엔드가 canvas로 생성한 공유 카드 PNG를 S3에 직접 업로드할 수 있도록 presigned PUT URL을 발급한다.
프론트엔드는 `presignedUrl`로 이미지를 업로드한 뒤, 같은 응답의 `imageUrl`을 `POST /api/share/v1/share-cards`에 전달한다.

**Request (Query String)**

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| extension | string | X | 확장자. 기본값은 "png". "jpg", "jpeg" 등 허용 |

**Response**

```json
{
  "presignedUrl": "https://<bucket>.s3.ap-northeast-2.amazonaws.com/share-cards/UUID.png?X-Amz-Signature=...",
  "imageUrl": "https://cdn.p5laris.life/share-cards/1/UUID.png"
}
```
* **`presignedUrl`**: 프론트엔드가 `PUT`으로 업로드할 임시 URL.
* **`imageUrl`**: 업로드 완료 후 공유 카드 생성 요청에 전달할 공개 이미지 URL.

---

### 9.2 POST `/api/share/v1/share-cards` 🔐

**설명**  
내 캐릭터 공유 카드를 생성한다. 공유 카드 이미지는 프론트엔드가 미리보기 화면과 같은 레이아웃으로 생성해 S3에 업로드하고, 백엔드는 전달받은 `imageUrl`의 소유자/경로를 검증한 뒤 공유 카드 레코드를 생성한다.
같은 유저가 같은 `imageUrl`로 다시 생성 요청을 보내면 기존 공유 카드를 반환한다.

**Request**

```json
{
  "characterId": 10,
  "headline": "오늘도 조금 반짝였어",
  "imageUrl": "https://cdn.p5laris.life/share-cards/1/UUID.png"
}
```

**Response**

```json
{
  "shareCardId": 800,
  "shareId": "sh_abc123",
  "imageUrl": "https://cdn.p5laris.life/share-cards/1/UUID.png",
  "shareUrl": "https://p5laris.life/share/sh_abc123"
}
```

---

### 9.3 GET `/api/share/v1/share-cards/{shareCardId}` 🔐

**설명**
내가 생성한 공유 카드 상세 정보를 조회한다. 공유 카드 소유자만 조회할 수 있다.

**Request**

```json
{
  "shareCardId": 800
}
```

**Response**

```json
{
  "shareCardId": 800,
  "characterName": "노바별",
  "imageUrl": "https://cdn.p5laris.life/share-cards/1/UUID.png",
  "shareUrl": "https://p5laris.life/share/sh_abc123"
}
```

---

### 9.4 POST `⚠️ /api/share/v1/share-events` 🔐

**설명**  
사용자가 공유 버튼을 눌렀다는 이벤트를 저장한다. 실제 외부 SNS 게시 여부는 현재 검증하지 않고, 하루 1회 공유 시도 보상 대상 여부를 `share_logs`에 기록한다.
오늘 첫 보상 대상이면 character 모듈은 `share_logs`와 `character_outbox_events`를 같은 짧은 트랜잭션에 저장한다. outbox 이벤트는 `aggregate_type='SHARE_LOG'`, `aggregate_id=shareLogId`, `event_type='SHARE_REWARD_REQUESTED'`, `payload jsonb={"userId":..., "rewardStarPiece":10}` 형식이다. 커밋 후 `ShareRewardDispatcher`가 user wallet gRPC `EarnStarPiece`를 즉시 호출한다. 성공 시 `wallet.starPiece`는 적립 후 지갑 잔액이며, 이미 오늘 보상을 받은 요청은 기존 보상 로그와 현재 지갑 잔액 기준으로 replay 응답을 반환한다. 즉시 지급 실패 시 API는 `SHARE_REWARD_FAILED`로 실패하고, 남은 outbox row는 스케줄러가 재처리한다.

**Request**

```json
{
  "shareCardId": 800,
  "platform": "X",
  "shareType": "WEB_SHARE_API",
  "idempotencyKey": "share-reward-20260515-uuid"
}
```

**Response**

```json
{
  "shareEventId": 810,
  "rewardPaid": true,
  "rewardStarPiece": 10,
  "wallet": {
    "starPiece": 110
  }
}
```

---

### 9.5 GET `/api/share/v1/share-events/today` 🔐

**설명**  
로그인한 유저가 오늘 날짜의 공유 보상을 이미 수령했는지 여부를 조회한다. 프론트엔드 버튼 상태 및 배너 표시에 사용된다.
오늘 공유 이력이 없으면 `rewardClaimed=false`, `lastSharedAt=""`로 반환한다.

**Request**

```json
{}
```

**Response**

```json
{
  "rewardClaimed": true,
  "lastSharedAt": "2026-05-19T09:22:24.400Z"
}
```

---

### 9.6 GET `💾 /api/share/v1/share-links/{shareId}` Public

**설명**  
외부 사용자가 공유 링크로 들어왔을 때 카드 정보를 조회한다.

**Request**

```json
{
  "shareId": "sh_abc123"
}
```

**Response**

```json
{
  "shareId": "sh_abc123",
  "characterName": "노바별",
  "imageUrl": "https://cdn.p5laris.life/share-cards/1/UUID.png",
  "headline": "오늘도 조금 반짝였음.",
  "signupUrl": "https://p5laris.life/signup?shareId=sh_abc123"
}
```

---

### 9.7 POST `/api/share/v1/share-clicks` Public

**설명**
공개 공유 링크 클릭을 기록한다. 현재 구현은 클릭 정보를 로그로 남기고 `recorded=true`를 반환한다.

**Request**

```json
{
  "shareId": "sh_abc123",
  "referrer": "https://example.com",
  "utmSource": "kakao",
  "utmMedium": "share",
  "utmCampaign": "mission-card"
}
```

**Response**

```json
{
  "shareId": "sh_abc123",
  "recorded": true
}
```

---

### 9.8 GET `/share/{shareId}` Public

**설명**
외부 SNS, 메신저, 브라우저가 접근하는 공유 HTML이다. 응답 HTML에는 `og:title`, `og:description`, `og:image`, `og:url` 메타 태그가 포함된다.

**Response**

```html
<!doctype html>
<html lang="ko">
  <head>
    <meta property="og:title" content="Polaris" />
    <meta property="og:image" content="https://cdn.p5laris.life/share-cards/1/UUID.png" />
  </head>
  <body>...</body>
</html>
```

---

## 10. 출석

### 10.1 POST `⚠️ /api/attendance/v1/attendance-records` 🔐

**설명**  
오늘 출석을 기록하고 출석 보상을 지급한다. 하루 1회만 생성되어야 한다.

**Request**

```json
{}
```

**Response**

```json
{
  "id": 1,
  "attendanceDate": "2026-05-15",
  "rewardStarPiece": 10,
  "streakCount": 1
}
```

---

### 10.2 GET `/api/attendance/v1/attendance-records` 🔐

**설명**  
달력 UI에 매칭하기 위해 특정 월(Month)의 내 출석 기록 리스트를 조회한다.

**Request**

```json
{
  "year": 2026,
  "month": 5
}
```

**Response**

```json
{
  "records": [
    {
      "id": 1,
      "attendanceDate": "2026-05-01",
      "rewardStarPiece": 10,
      "streakCount": 1
    },
    {
      "id": 2,
      "attendanceDate": "2026-05-15",
      "rewardStarPiece": 10,
      "streakCount": 2
    }
  ]
}
```

---

## 11. 알림

### 11.1 GET `/api/notification/v1/notifications` 🔐

**설명**  
앱 내부 알림 목록을 조회한다.

**Request**

```json
{
  "read": false,
  "cursor": null,
  "size": 20
}
```

**Response**

```json
{
  "items": [
    {
      "id": 600,
      "notificationType": "MISSION",
      "title": "작은 미션 하나가 기다리고 있어요",
      "message": "오늘 별조각 하나가 아직 안 태어났어.",
      "targetType": "MISSION",
      "targetId": 101,
      "read": false,
      "createdAt": "2026-05-15T18:00:00+09:00"
    }
  ],
  "pageInfo": {
    "nextCursor": null,
    "hasNext": false,
    "size": 20
  }
}
```

---

### 11.2 PATCH `/api/notification/v1/notifications/{notificationId}` 🔐

**설명**  
알림을 읽음 처리한다.

**Request**

```json
{
  "read": true
}
```

**Response**

```json
{
  "id": 600,
  "read": true,
  "updatedAt": "2026-05-15T18:45:00+09:00"
}
```

---

### 11.3 POST `/api/notification/v1/subscriptions/` 🔐

**설명**  
FCM 푸시 알림을 허용하고 토큰을 저장해 구독을 시작한다. 같은 토큰이 다시 들어오면 기존 토큰 정보를 갱신한다.

**Request**

```json
{
  "token": "FCM-token"
}
```

**Response**

```json
{
  "id": 900,
  "createdAt": "2026-05-15T18:45:00+09:00"
}
```

---

### 11.4 GET `/api/notification/v1/settings` 🔐

**설명**
로그인한 사용자의 알림 수신 설정을 조회한다. 설정 row가 아직 없으면 기본 설정을 반환한다.

**Request**

```json
{}
```

**Response**

```json
{
  "pushEnabled": true,
  "missionOfferEnabled": true,
  "characterStateEnabled": true,
  "dailyReminderEnabled": true,
  "quietHoursEnabled": false,
  "quietHoursStart": "22:00",
  "quietHoursEnd": "08:00"
}
```

---

### 11.5 PATCH `/api/notification/v1/settings` 🔐

**설명**
알림 수신 설정을 갱신한다. 모든 필드를 함께 전달한다. 시간 필드는 `HH:mm` 형식이다.

**Request**

```json
{
  "pushEnabled": true,
  "missionOfferEnabled": true,
  "characterStateEnabled": true,
  "dailyReminderEnabled": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00",
  "quietHoursEnd": "08:00"
}
```

**Response**

```json
{
  "pushEnabled": true,
  "missionOfferEnabled": true,
  "characterStateEnabled": true,
  "dailyReminderEnabled": true,
  "quietHoursEnabled": true,
  "quietHoursStart": "22:00",
  "quietHoursEnd": "08:00"
}
```

---

### 11.6 내부 gRPC `NotificationService.SendPushNotification`

**설명**
mission, character 등 내부 서비스가 알림 저장과 FCM 푸시 발송을 요청할 때 사용한다. notification 서비스는 먼저 `notifications` row를 만들고, FCM 발송은 `notification_push_deliveries`에 시도 결과를 남긴다.

**gRPC Request**

```json
{
  "userId": 1,
  "title": "새 미션이 도착했어요",
  "body": "오늘의 작은 루틴을 시작해 볼까요?",
  "notificationType": "NOTIFICATION_TYPE_MISSION",
  "targetType": "TARGET_TYPE_MISSION",
  "targetId": 101
}
```

**gRPC Response**

```json
{
  "success": true
}
```

---

### 11.7 내부 gRPC `NotificationService.GetUnreadNotificationCount`

**설명**
홈 통합 조회에서 `notifications.unreadCount`를 채우기 위해 gateway가 notification 서비스에 요청하는 내부 gRPC API다. 별도 외부 REST endpoint로 노출하지 않는다.

**gRPC Request**

```json
{
  "userId": 1
}
```

**gRPC Response**

```json
{
  "unreadCount": 2
}
```

---

## 12. 주요 상태 / Enum

### 12.1 미션 상태

| 상태 | 의미 |
|---|---|
| `GENERATED` | 생성됐지만 아직 사용자에게 노출되지 않음 |
| `OFFERED` | 현재 사용자에게 제안됨 |
| `ANSWERING` | 완료 클릭 후 질문 답변 중 |
| `COMPLETED` | 답변 완료 후 보상 지급 완료 |
| `REJECTED` | 사용자가 거절 |
| `EXPIRED` | 날짜 변경 등으로 만료 |

### 12.2 캐릭터 상태

| 필드 | 의미 | 화면 라벨 예시 |
|---|---|---|
| `hunger` | 높을수록 든든함 | 든든함 / 출출함 / 배고픔 |
| `energy` | 높을수록 기운 있음 | 말짱함 / 졸림 / 피곤함 |
| `affection` | 높을수록 가까움 | 가까움 / 조용함 / 쓸쓸함 |

### 12.2.1 캐릭터 성장 / 서사 / 대화

| 구분 | 값 | 의미 |
|---|---|---|
| `growthStage` | `BABY` | Lv.1, 새싹 단계 |
| `growthStage` | `GROWING` | Lv.2, 성장 단계 |
| `growthStage` | `MATURE` | Lv.3, 성숙 단계 |
| `fragmentType` | `COMMON` | 해금 이력 없이도 보여줄 수 있는 기본 반응 |
| `fragmentType` | `LORE` | 캐릭터 세계관과 과거가 담긴 기억 조각 |
| `fragmentType` | `EASTER_EGG` | 특정 조건에서 열리는 숨은 반응 |
| `triggerType` | `TAP` | 캐릭터 터치/말 걸기 상호작용 |
| `triggerType` | `LEVEL_UP` | 레벨업 직후 반응 |
| `triggerType` | `LOW_HUNGER` | 포만감이 낮을 때 |
| `triggerType` | `LOW_ENERGY` | 에너지가 낮을 때 |
| `triggerType` | `LOW_AFFECTION` | 애정도가 낮을 때 |
| `triggerType` | `NIGHT` | 밤 시간대 반응 |
| `triggerType` | `MIDNIGHT` | 자정 이후 새벽 반응 |
| SSE event | `meta` | 스트림 시작 메타데이터 |
| SSE event | `delta` | 이어 붙일 응답 조각 |
| SSE event | `done` | 스트림 종료 메타데이터 |

### 12.3 아이템

| 필드 | 값 |
|---|---|
| `itemType` | `SKIN`, `CONSUMABLE` |
| `effectType` | `FOOD`, `REST`, `PLAY` |
| `actionType` | `FEED`, `SLEEP`, `PLAY` |

### 12.4 별조각 거래 사유

| reason | 설명 |
|---|---|
| `MISSION_REWARD` | 미션 완료 보상 |
| `ITEM_PURCHASE` | 아이템 구매 |
| `ATTENDANCE_REWARD` | 출석 보상 |
| `SHARE_REWARD` | 공유 시도 보상 |
| `CARE_ACTION` | 별조각 직접 차감형 돌봄 정책 도입 시 사용. 현재 돌봄은 소모품 수량 차감 기준 |

### 12.5 AI 문구 생성 상태

| 상태 | 의미 |
|---|---|
| `SUCCESS` | AI 또는 local generator 문구를 사용 |
| `FALLBACK` | 생성 결과를 사용할 수 없어 미션 템플릿 fallback 문구 사용 |
| `FAILED` | 문구 생성 실패 |

### 12.6 AI 에러 타입

| 타입 | 의미 |
|---|---|
| `TIMEOUT` | provider 응답 지연 |
| `RATE_LIMIT` | provider 요청 제한 |
| `RATE_LIMIT_UNAVAILABLE` | rate limit 저장소 장애로 provider 호출 차단 |
| `INVALID_OUTPUT` | 응답 구조 오류 |
| `POLICY_VIOLATION` | 서비스 문구 정책 위반 |
| `PROVIDER_ERROR` | provider 내부 오류 |
| `UNKNOWN` | 분류되지 않은 오류 |

---

## 13. 주요 에러 코드

| 코드 | 상황 |
|---|---|
| `UNAUTHORIZED` | 인증 실패 |
| `FORBIDDEN` | 권한 없음 |
| `USER_NOT_FOUND` | 사용자 없음 |
| `INVALID_WEATHER_REGION` | 사용할 수 없는 날씨 권역 코드 |
| `CHARACTER_NOT_FOUND` | 캐릭터 없음 |
| `CHARACTER_NAME_INVALID` | 캐릭터 이름 형식 오류 |
| `ONBOARDING_REQUIRED` | 온보딩 미완료로 미션 사용 불가 |
| `MISSION_NOT_FOUND` | 미션 없음 |
| `MISSION_TEMPLATE_NOT_FOUND` | 사용할 수 있는 미션 템플릿 없음 |
| `MISSION_INVALID_STATUS` | 상태 전이 불가 |
| `MISSION_ACTIVE_ALREADY_EXISTS` | 이미 진행 중인 미션 존재 |
| `MISSION_DAILY_LIMIT_EXCEEDED` | 일일 미션 완료 보상 제한 초과 |
| `MISSION_REJECT_LIMIT_EXCEEDED` | 일일 미션 거절 제한 초과 |
| `MISSION_ALREADY_COMPLETED` | 이미 완료된 미션 |
| `MISSION_ANSWER_INVALID` | 완료 답변 길이 오류 |
| `MISSION_FEEDBACK_INVALID` | 미션 피드백 형식 오류 |
| `MISSION_SERVICE_UNAVAILABLE` | 미션 서비스 일시 장애 |
| `STAR_PIECE_NOT_ENOUGH` | 별조각 부족 |
| `DUPLICATED_IDEMPOTENCY_KEY` | 중복 요청 |
| `ITEM_NOT_FOUND` | 아이템 없음 |
| `ITEM_ALREADY_OWNED` | 이미 보유한 장착형 아이템 |
| `ITEM_NOT_OWNED` | 보유하지 않은 아이템 |
| `ITEM_QUANTITY_NOT_ENOUGH` | 소모품 수량 부족 |
| `ATTENDANCE_ALREADY_CHECKED` | 오늘 출석 완료 |
| `SHARE_REWARD_ALREADY_PAID` | 오늘 공유 시도 보상 대상 기록 완료 |
| `AI_INVALID_REQUEST` | AI 문구 생성 요청 값 오류 |
| `AI_FALLBACK_INVALID` | fallback 문구 정책 검증 실패 |
| `AI_GENERATION_FAILED` | AI 문구 생성 실패 |
| `AI_DUPLICATED_REQUEST` | 이미 처리된 AI 요청 ID |

---



