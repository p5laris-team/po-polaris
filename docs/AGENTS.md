# AGENTS.md

이 문서는 Polaris 저장소에서 작업하는 모든 AI 코딩 에이전트를 위한 루트 지침이다.
도구 종류와 무관하게 이 파일을 우선 읽고, 아래 규칙을 코드 작성·수정·리뷰 기준으로 삼는다.

---

## 1. 프로젝트 요약

Polaris는 사용자가 선택한 캐릭터가 온보딩 설문을 통해 사용자의 생활 맥락을 파악하고, seed 미션과 AI 문구 기반으로 작은 미션을 제안하며, 미션 완료 후 별조각을 지급하는 루틴 서비스다.

현재 MVP의 핵심 루프는 다음이다.

```text
로그인
→ 캐릭터 선택/생성
→ 온보딩 설문 완료
→ 현재 미션 1개 제안
→ 거절 또는 완료
→ 완료 질문 1개 답변
→ 별조각 지급
→ 아이템 구매/장착 또는 돌봄
→ 캐릭터 카드 공유
```

현재 MVP에서 제외된 기능은 구현하지 않는다.

```text
업적
광고
결제
별조각 구매
Mock 별조각 구매
구독
가챠
지도 기반 인증
친구/파티/채팅/리그
레벨업 외형 진화
액세서리 아이템
복잡한 관리자 기능
```

오래된 문서에 업적이나 광고가 남아 있어도 현재 MVP 기준에서는 제외한다. 사용자가 명시적으로 다시 포함한다고 말하기 전까지 API, DB, 서비스, 테스트를 만들지 않는다.

---

## 2. 현재 프로젝트 구조

현재 저장소는 Gradle 멀티모듈 구조다.

```text
.
├── gateway      # 외부 REST API 진입점. 각 도메인 gRPC 서비스 호출
├── user         # 사용자/인증 도메인 gRPC 서비스
├── character    # 캐릭터 도메인 gRPC 서비스
├── item         # 아이템 도메인 gRPC 서비스
├── mission      # 미션 도메인 gRPC 서비스
├── notification # 알림 도메인 gRPC 계약
├── ai           # AI 미션/문구 생성 도메인 gRPC 서비스
├── Operation    # 운영 도메인 gRPC 서비스
├── proto        # gRPC/protobuf 계약
├── build.gradle
├── settings.gradle
└── gradlew
```

기본 기술 스택은 다음이다.

```text
Java 21
Gradle
Spring Boot
Spring Web gateway
Spring Data JPA domain services
PostgreSQL
gRPC
Protocol Buffers
Lombok 일부 모듈
JUnit 5
```

현재 외부 클라이언트는 `gateway`의 REST API만 호출한다. `user`, `character`, `item`, `mission`, `ai` 모듈은 gRPC 서버 역할을 한다.

---

## 3. 문서 우선순위

코딩 전에 기준 문서를 먼저 확인한다.

우선순위는 다음과 같다.

1. 사용자가 현재 대화에서 명시한 요구사항
2. 최신 API 명세서
3. 최신 ERD
4. 백엔드 정책 문서
5. PRD
6. 기존 코드

서로 충돌하면 임의로 선택하지 않는다. 다음 형식으로 멈추고 질문한다.

```text
충돌 발견:
- API 명세: ...
- ERD: ...
- 기존 코드: ...

가능한 선택지:
1. ... 장점 / 단점
2. ... 장점 / 단점

추천: ...
확인 필요: ...
```

---

## 4. 코딩 전에 반드시 생각할 것

추측하지 않는다. 모호한 요구사항을 숨기지 않는다.

작업 시작 전 다음을 확인한다.

```text
1. 목표가 무엇인가?
2. 어떤 파일을 바꿀 것인가?
3. 바꾸지 말아야 할 것은 무엇인가?
4. API 명세/ERD/정책과 충돌하지 않는가?
5. 더 단순한 방법은 없는가?
6. 성공 여부를 어떻게 검증할 것인가?
```

요구사항이 여러 방식으로 해석될 수 있으면 모든 해석을 제시한다.

예시:

```text
"미션 완료 API 수정"은 다음 중 하나일 수 있다.
1. REST endpoint 변경
2. gRPC proto 변경
3. mission 상태 전이 변경
4. 별조각 지급 트랜잭션 변경
5. 완료 답변 validation 변경

어느 범위인지 확인 필요.
```

불확실하면 질문한다. 단, 사용자가 명확한 산출물을 요구했고 합리적 기본값이 있는 경우에는 그 가정을 짧게 밝히고 진행한다.

---

## 5. 단순함 우선 원칙

필요한 최소 코드만 작성한다.

금지:

```text
요청하지 않은 기능 추가
미래 확장을 위한 추상화 선반영
불필요한 디자인 패턴 도입
새 라이브러리 임의 추가
설정 가능성 과잉 설계
불가능한 시나리오까지 방어하는 코드
사용자가 요청하지 않은 리팩토링
```

판단 기준:

```text
50줄로 가능한 작업을 200줄로 만들지 않는다.
한 번만 쓰는 로직은 무리하게 공통화하지 않는다.
요구사항이 아직 없으면 인터페이스/전략/팩토리를 만들지 않는다.
복잡성이 필요해진 시점에 리팩토링한다.
```

단순한 구현과 복잡한 구현이 모두 가능하면 단순한 쪽을 우선 제안한다.

---

## 6. 수술적 변경 원칙

요청받은 부분만 변경한다.

기존 코드를 편집할 때 금지:

```text
인접 코드 정리
주석 문체 개선
공백/포맷 전체 정리
필요 없는 변수명 변경
관련 없는 사용되지 않는 코드 삭제
동작이 멀쩡한 코드 리팩토링
취향에 따른 패키지 구조 변경
```

변경으로 인해 새로 사용되지 않게 된 import, 변수, 함수는 제거한다.
단, 기존에 이미 사용되지 않던 코드는 삭제하지 말고 보고만 한다.

모든 변경 줄은 사용자의 요청과 직접 연결되어야 한다.

---

## 7. 목표 중심 실행

모든 작업은 검증 가능한 목표로 바꾼다.

나쁜 목표:

```text
인증을 개선한다.
미션 로직을 정리한다.
에러 처리를 좋게 만든다.
```

좋은 목표:

```text
Google OAuth 로그인 성공 시 accessToken, refreshToken, user를 반환한다.
OFFERED 상태 미션만 완료 질문을 시작할 수 있다.
이미 완료된 missionId는 별조각 보상을 다시 지급하지 않는다.
아이템 구매 시 잔액 부족이면 user_items와 wallet 모두 변경되지 않는다.
```

복잡한 작업은 다음 형식으로 계획한다.

```text
1. [작업] → verify: [테스트/명령/확인 방법]
2. [작업] → verify: [테스트/명령/확인 방법]
3. [작업] → verify: [테스트/명령/확인 방법]
```

버그 수정은 가능하면 먼저 재현 테스트를 만든다.

```text
1. 실패하는 테스트로 버그 재현
2. 최소 코드로 수정
3. 같은 테스트 통과 확인
4. 관련 기존 테스트 통과 확인
```

---

## 8. 작업 완료 기준

작업이 끝났다고 말하려면 아래를 확인한다.

```text
요구사항 반영 완료
변경 범위가 요청과 직접 관련 있음
API 명세/ERD/정책과 충돌 없음
동시성/멱등성 민감 영역 확인
소유권/인증 체크 확인
관련 테스트 실행 또는 실행 불가 사유 명시
불필요한 import/build artifact/임시 파일 없음
```

최종 보고 형식:

```text
변경 요약:
- ...

검증:
- 실행: ./gradlew :module:test
- 결과: pass / fail / 실행 불가 사유

주의/남은 이슈:
- ...
```

---

## 9. 빌드와 테스트 명령

루트에서 실행한다.

```bash
./gradlew test
./gradlew build
```

모듈별 테스트:

```bash
./gradlew :gateway:test
./gradlew :user:test
./gradlew :character:test
./gradlew :item:test
./gradlew :mission:test
./gradlew :ai:test
./gradlew :proto:test
```

proto 변경 후:

```bash
./gradlew :proto:generateProto
./gradlew :proto:build
```

특정 모듈 실행:

```bash
./gradlew :gateway:bootRun
./gradlew :user:bootRun
./gradlew :character:bootRun
./gradlew :item:bootRun
./gradlew :mission:bootRun
./gradlew :ai:bootRun
```

주의:

```text
build/, .gradle/, .idea/ 산출물은 수정 대상으로 보지 않는다.
빌드 산출물을 새로 생성했더라도 사용자 요청이 없으면 커밋 대상에 포함하지 않는다.
```

---

## 10. 의존성 및 버전 규칙

새 의존성은 임의로 추가하지 않는다.

새 라이브러리를 추가해야 한다면 먼저 다음을 제시한다.

```text
필요한 이유
대안
추가되는 의존성 이름과 버전
영향받는 모듈
보안/라이선스/운영 리스크
```

현재 관찰된 특징:

```text
대부분의 서비스 모듈은 Spring Boot 3.5.14를 사용한다.
ai 모듈은 현재 build.gradle에 Spring Boot 4.0.6이 설정되어 있다.
```

이 차이를 임의로 정리하지 않는다. 버전 통일이 필요해 보이면 변경하지 말고 먼저 보고한다.

---

## 11. 아키텍처 경계

### 11.1 gateway

`gateway`는 외부 REST API의 진입점이다.

규칙:

```text
gateway는 HTTP request/response, 인증, validation, REST DTO를 담당한다.
gateway는 도메인별 gRPC client를 통해 내부 서비스를 호출한다.
gateway에 JPA Entity/Repository를 만들지 않는다.
gateway는 DB에 직접 접근하지 않는다.
gateway의 REST 응답에 protobuf 객체를 그대로 노출하지 않는다.
```

### 11.2 domain service modules

`user`, `character`, `item`, `mission`, `ai`는 각자 gRPC 서버로 동작한다.

규칙:

```text
각 서비스는 자기 도메인의 DB와 비즈니스 규칙을 소유한다.
다른 도메인의 Repository를 직접 호출하지 않는다.
다른 도메인 정보가 필요하면 gRPC client 또는 명시적인 application service를 사용한다.
도메인 간 순환 호출을 만들지 않는다.
```

### 11.3 proto

`proto`는 내부 서비스 계약의 기준이다.

규칙:

```text
proto 변경은 API 계약 변경으로 간주한다.
proto field number는 절대 재사용하지 않는다.
기존 field의 의미를 바꾸지 않는다.
새 필드는 새 번호로 추가한다.
enum의 0번 값은 *_UNSPECIFIED 형태를 유지한다.
패키지 버전은 현재 v1을 유지한다.
```

---

## 12. 패키지 구조 규칙

현재 기본 구조를 유지한다.

```text
p5laris.{module}
└── domain
    ├── api
    ├── application
    ├── domain
    └── infrastructure
```

이미 존재하는 패키지 스타일을 우선한다.

권장 역할:

```text
api              REST/gRPC controller, request/response DTO
application      use case service, transaction boundary, domain service
domain           entity, value object, domain policy, repository
infrastructure   external client, gRPC adapter, persistence adapter
```

대규모 패키지 재구성은 금지한다. 구조 변경이 필요하면 먼저 설계안을 제시한다.

---

## 13. REST API 규칙

외부 REST API는 도메인별 버전을 사용한다.

```text
/api/{domain}/v1/{resource}
```

예시:

```text
/api/auth/v1/google/sessions
/api/user/v1/users/me
/api/character/v1/characters/me
/api/mission/v1/missions/current
/api/item/v1/items
/api/wallet/v1/wallets/me
/api/share/v1/share-cards
/api/notification/v1/notifications
```

REST 규칙:

```text
자원 중심 URL을 사용한다.
불필요한 동사를 endpoint에 넣지 않는다.
목록 조회는 cursor 기반 pagination을 우선한다.
인증 필요 API는 Bearer JWT 기준으로 설계한다.
응답은 ApiResponse로 감싼다.
실패 응답에는 timestamp, status, code, message, path를 포함한다.
retryAfterSeconds는 rate limit 등 필요한 경우에만 포함한다.
```

성공 응답:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

실패 응답:

```json
{
  "success": false,
  "data": null,
  "error": {
    "timestamp": "2026-05-15T13:42:10+09:00",
    "status": 400,
    "code": "MISSION_INVALID_STATUS",
    "message": "현재 상태에서는 미션을 완료할 수 없습니다.",
    "path": "/api/mission/v1/missions/10/completion-answers"
  }
}
```

현재 `gateway`에 남아 있는 `/api/user`, `/api/character`, `/api/item`, `/api/mission`, `/api/ai` 형태의 ping-pong 엔드포인트는 스캐폴딩/헬스체크 성격으로 본다. 실제 기능 구현 시에는 최신 API 명세의 도메인별 버전 URL을 따른다. 기존 스캐폴딩 경로를 최종 API로 확장하지 않는다.

REST와 gRPC 경계:

```text
외부 클라이언트 ↔ gateway REST DTO ↔ gateway gRPC client ↔ domain service gRPC API
```

규칙:

```text
gRPC message를 REST 응답으로 그대로 반환하지 않는다.
gRPC status/error는 gateway에서 ApiResponse 실패 응답으로 변환한다.
내부 gRPC 메서드명은 서비스 계약이므로 proto 변경 체크리스트를 따른다.
REST URL 버전과 proto package 버전은 별개로 관리한다.
```

---

## 14. 인증과 소유권

인증이 필요한 API는 반드시 현재 사용자 기준으로 동작한다.

금지:

```text
userId를 body로 받아 그대로 신뢰
다른 사용자의 characterId, missionId, itemId 조회 허용
Entity를 그대로 응답
토큰/비밀번호/인증 코드를 로그에 출력
```

필수:

```text
JWT subject 또는 인증 principal에서 userId를 가져온다.
path variable의 리소스가 현재 사용자 소유인지 검증한다.
권한 없는 접근은 FORBIDDEN 또는 NOT_FOUND 정책 중 하나로 일관되게 처리한다.
관리자 API는 MVP 범위가 아니면 만들지 않는다.
```

---

## 15. 예외 처리 규칙

예외 응답은 일관되어야 한다.

규칙:

```text
비즈니스 예외는 전용 예외 타입으로 만든다.
RuntimeException을 직접 던지지 않는다.
null 반환으로 실패를 표현하지 않는다.
ControllerAdvice에서 HTTP 응답을 통합 처리한다.
e.printStackTrace() 금지.
로그는 log.error("message", exception) 형식을 사용한다.
```

에러 코드는 도메인별로 명확하게 관리한다.

예시:

```text
MISSION_NOT_FOUND
MISSION_INVALID_STATUS
MISSION_DAILY_LIMIT_EXCEEDED
STAR_PIECE_NOT_ENOUGH
STAR_PIECE_DUPLICATED
ITEM_NOT_FOUND
ITEM_ALREADY_OWNED
ITEM_NOT_OWNED
CHARACTER_NOT_FOUND
CHARACTER_STATUS_INVALID
SHARE_REWARD_ALREADY_PAID
ATTENDANCE_ALREADY_CHECKED
```

업적/광고 에러 코드는 현재 MVP에서 만들지 않는다.

---

## 16. 트랜잭션 규칙

트랜잭션은 application service의 public 메서드에 둔다.

규칙:

```text
private 메서드에 @Transactional 금지
self-invocation으로 트랜잭션이 적용된다고 가정하지 않는다.
readOnly=true 메서드에서 save/update/delete 금지
Propagation 변경은 명확한 이유가 있을 때만 사용한다.
트랜잭션 경계는 DB 작업 단위가 아니라 비즈니스 유스케이스 단위로 잡는다.
```

동시성 민감 API는 반드시 중복 실행을 고려한다.

대상:

```text
미션 생성/제안 일일 제한
미션 완료 및 별조각 보상 지급
아이템 구매 및 별조각 차감
소모품 사용 및 수량 차감
캐릭터 돌봄 액션
공유 시도 보상 기록
출석 보상 지급
토큰 재발급
```

필수 질문:

```text
같은 요청이 두 번 오면 안전한가?
동시에 두 요청이 오면 안전한가?
DB 트랜잭션이 실패하면 외부 상태와 불일치하지 않는가?
```

---

## 17. 멱등성 규칙

보상 지급, 잔액 차감, 수량 차감이 있는 API는 멱등성을 고려한다.

권장 idempotencyKey 예시:

```text
MISSION_REWARD:{missionId}
ITEM_PURCHASE:{requestId}
CARE_ACTION:{requestId}
SHARE_REWARD:{userId}:{yyyyMMdd}
ATTENDANCE:{userId}:{yyyyMMdd}
TOKEN_REFRESH:{refreshTokenId}:{nonce}
```

중복 요청 처리:

```text
같은 idempotencyKey로 이미 성공한 요청이면 기존 결과를 반환한다.
같은 idempotencyKey로 다른 요청 body가 들어오면 충돌로 처리한다.
중복 보상 지급은 절대 허용하지 않는다.
```

---

## 18. JPA 및 DB 규칙

기본 규칙:

```text
Entity를 API 응답으로 직접 반환하지 않는다.
연관관계는 기본 LAZY로 둔다.
N+1 가능성이 있는 조회는 fetch join, EntityGraph, batch size, 분리 조회 중 하나를 검토한다.
컬렉션 fetch join + pagination 조합은 피한다.
DB 제약과 application validation을 함께 사용한다.
```

Repository 규칙:

```text
다른 도메인의 Repository를 직접 주입하지 않는다.
Native query 사용 시 문자열 결합 금지.
동적 조건은 안전한 파라미터 바인딩을 사용한다.
대량 조회는 cursor 기반을 우선한다.
```

DB 변경 시 확인:

```text
ERD와 맞는가?
unique/check/index가 필요한가?
동시성 민감 row에 lock 또는 version이 필요한가?
마이그레이션이 필요한가?
```

---

## 19. 시간 처리 규칙

시간 정책은 일관되어야 한다.

```text
java.util.Date 사용 금지.
DB 저장 기준은 가능하면 UTC Instant를 사용한다.
사용자에게 보여주는 날짜/시간은 Asia/Seoul 등 명시된 timezone을 고려한다.
"오늘" 기준은 서비스 정책에 맞춰 명확히 한다.
미션 일일 제한, 공유 시도 보상 기록 1일 1회, 출석은 같은 날짜 기준을 사용한다.
현재 시각은 테스트 가능하도록 Clock 주입을 검토한다.
```

하드코딩 금지:

```text
LocalDate.now()를 비즈니스 로직 깊숙한 곳에 직접 호출
서버 로컬 timezone에 의존
문자열 날짜 비교
```

---

## 20. Polaris 도메인 불변식

### 20.1 캐릭터

```text
MVP 캐릭터는 NOVA, MUMU, JJORY 3종이다.
사용자는 MVP에서 활성 캐릭터 1개를 가진다.
캐릭터 이름은 1~10자 정책을 지킨다.
상태값은 내부적으로 높을수록 좋은 값이다.
상태는 fullness, energy, affection 기준을 사용한다.
```

금지:

```text
hunger를 높을수록 나쁜 값으로 구현
sleepiness, cleanliness, boredom을 MVP 상태로 추가
레벨업 외형 진화 구현
액세서리 슬롯 구현
```

상태 범위:

```text
0 <= fullness <= 100
0 <= energy <= 100
0 <= affection <= 100
```

### 20.2 돌봄 액션

```text
FEED      → fullness 회복
SLEEP     → energy 회복
PLAY      → affection 회복
```

규칙:

```text
상태는 100을 초과하지 않는다.
돌봄 액션은 보유한 CONSUMABLE 아이템을 사용해야 한다.
별조각은 돌봄에서 직접 차감하지 않고 아이템 구매 시 차감한다.
소모품 사용 시 user_items.quantity를 1 감소시킨다.
돌봄 기록은 character_care_logs에 남긴다.
```

### 20.3 미션

```text
미션은 한 번에 하나씩 제안한다.
하루 보상 완료는 최대 20회다.
하루 거절은 최대 10회다.
CHALLENGE 미션은 하루 1회까지만 제안한다.
오늘 제안된 미션은 stack으로 관리한다.
자정 또는 날짜 변경 시 당일 stack은 만료될 수 있다.
거절은 실패가 아니다.
거절해도 별조각은 차감하지 않는다.
```

상태 전이는 API 명세/ERD의 최신 상태명을 따른다. 상태명이 서로 다르면 코딩하지 말고 확인한다.

기본 정책:

```text
현재 제안 상태에서만 거절 가능
현재 제안 상태에서만 완료 질문 시작 가능
완료 질문 답변 후에만 COMPLETED 처리
이미 COMPLETED된 미션은 다시 보상 지급 금지
이미 REJECTED된 미션은 완료 금지
```

### 20.4 미션 완료 답변

```text
질문은 1개만 제공한다.
답변 방식은 텍스트다.
답변은 최소 1자, 최대 300자다.
답변 품질을 과하게 판정하지 않는다.
욕설/비속어 필터링은 정책에 따라 적용한다.
```

사진 인증, 이모지 선택, 선택지 응답은 MVP에서 구현하지 않는다.

### 20.5 별조각

```text
별조각은 MVP에서 무료 재화다.
별조각 구매 API는 없다.
Mock 별조각 구매도 없다.
모든 별조각 증감은 star_piece_transactions에 기록한다.
미션 완료 보상은 missionId 기준 1회만 지급한다.
공유 시도 보상은 하루 1회만 지급 대상이 될 수 있다.
공유 보상은 share_logs와 character_outbox_events에 기록한 뒤 wallet 적립을 커밋 후 시도하고, 실패 시 outbox가 재처리한다.
아이템 구매 실패 시 별조각은 차감하지 않는다.
```

금지:

```text
wallet 잔액만 변경하고 거래 내역 누락
테스트용 구매 API 추가
운영자 임의 지급 API 추가
결제/PG 관련 테이블 추가
```

### 20.6 아이템

MVP 아이템 유형:

```text
SKIN
FOOD
REST
TOY
```

규칙:

```text
SKIN은 중복 구매할 수 없다.
SKIN은 한 번에 하나만 장착한다.
FOOD/REST/TOY는 소모품이며 여러 개 보유 가능하다.
소모품 사용 시 quantity를 1 감소시킨다.
아이템 구매와 별조각 차감은 하나의 트랜잭션으로 처리한다.
```

금지:

```text
ACCESSORY 구현
가챠 구현
현금 가격 구현
결제 상품 구현
```

### 20.7 공유

```text
공유 카드를 생성할 수 있다.
공유 버튼 클릭 또는 공유 시도 이벤트를 저장한다.
공유 시도 보상 대상 여부는 하루 1회만 기록한다.
실제 외부 SNS 게시 여부는 MVP에서 검증하지 않는다.
공유 유입 추적은 shareId와 UTM 로그 흐름을 따른다.
공유 후 회원가입 referral 저장은 MVP 범위 밖이다.
```

공유 시도 보상 기록은 share intent reward 기준이다.

### 20.8 출석

```text
사용자는 하루 한 번 출석할 수 있다.
출석 보상은 하루 1회만 지급한다.
중복 출석 요청은 멱등하게 처리한다.
```

### 20.9 알림

```text
앱 내부 알림은 MVP 대상이다.
FCM 푸시 알림은 토큰 등록, 사용자 설정, 방해 금지 시간 정책을 따른다.
하루 최대 3회 정책을 고려한다.
죄책감 유발 문구를 만들지 않는다.
캐릭터 말투를 반영한다.
```

### 20.10 AI 미션

```text
AI는 개인화 context를 바탕으로 미션 후보와 캐릭터 문구를 생성한다.
AI 응답은 구조화 검증 후 저장한다.
AI 실패 시 seed fallback 문구/템플릿을 사용한다.
미션 제목, 보상, 카테고리, 난이도는 mission 서버의 정책 검증을 통과해야 한다.
```

AI 실패 케이스:

```text
timeout
rate limit
JSON parsing 실패
금지 표현 포함
문장 길이 초과
정책 외 미션 생성
```

---

## 21. 보안 규칙

필수:

```text
모든 입력 DTO에 validation 적용
인증 사용자 소유권 검증
민감 정보 로그 출력 금지
Entity 직접 노출 금지
토큰은 응답이 필요한 경우에만 반환
환경 변수로 비밀값 관리
```

금지:

```text
password, accessToken, refreshToken 로그 출력
OAuth code 로그 출력
idempotencyKey 전체 로그 출력
SQL 문자열 결합
CORS 전체 허용을 운영 설정에 추가
application.yaml에 실제 secret 하드코딩
```

---

## 22. 로깅 규칙

로그는 문제 추적에 필요한 수준으로만 남긴다.

권장:

```text
요청 ID
userId 일부 또는 내부 ID
도메인 이벤트 타입
상태 전이 전/후
에러 코드
```

금지:

```text
개인정보 전체 출력
토큰 출력
OAuth code 출력
사용자 답변 전문 출력
대용량 JSON 전체 출력
```

비즈니스 이벤트는 필요 시 event_logs 또는 도메인 로그 테이블에 남긴다. 단, 현재 MVP에서 존재하지 않는 테이블을 임의로 만들지 않는다.

---

## 23. 테스트 규칙

테스트는 실제 실패 가능성을 검증해야 한다.

금지:

```text
assertNotNull(service)만 있는 테스트
항상 통과하는 의미 없는 테스트
구현 세부사항만 검증하는 과한 mock 테스트
테스트 없이 동시성 민감 로직 변경
```

권장 테스트 이름:

```text
미션이_OFFERED_상태이면_완료_질문을_시작할_수_있다
이미_완료된_미션은_별조각을_다시_지급하지_않는다
잔액이_부족하면_아이템_구매에_실패한다
공유_시도_보상은_하루에_한_번만_기록된다
```

테스트 우선순위:

```text
1. 별조각 지급/차감 정합성
2. 미션 상태 전이
3. 아이템 구매/사용 수량 차감
4. 공유 시도 기록/출석 보상 중복 방지
5. 인증/소유권 검증
6. API validation
```

---

## 24. 성능 규칙

성능 최적화는 근거가 있을 때만 한다.

금지:

```text
측정 없이 캐시 추가
측정 없이 async 처리 추가
요구사항 없이 Redis 추가
인덱스 남발
모든 조회에 fetch join 적용
```

검토 기준:

```text
목록 API는 cursor pagination 우선
캐릭터 타입/아이템 카탈로그처럼 자주 바뀌지 않는 조회는 캐싱 후보
사용자별 현재 상태/잔액은 무조건 캐싱하지 않는다
N+1 쿼리 여부 확인
대량 데이터 조회 시 limit 필수
```

---

## 25. 코드 스타일

현재 코드 스타일을 따른다.

```text
Java 21 문법 사용 가능
Spring annotation은 기존 방식 유지
생성자 주입 우선
DTO와 Entity 분리
모듈별 기존 package 이름 유지
불필요한 Lombok 추가 금지
```

스타일을 바꾸고 싶어도 작업 범위에 포함되지 않으면 바꾸지 않는다.

---

## 26. Proto 변경 체크리스트

proto를 변경할 때는 반드시 확인한다.

```text
기존 field number를 재사용하지 않았는가?
기존 field 의미를 바꾸지 않았는가?
UNSPECIFIED enum 값이 0인가?
REST API DTO와 gRPC message 경계가 명확한가?
변경 후 :proto:generateProto를 실행했는가?
영향받는 gateway client/service를 수정했는가?
영향받는 gRPC server controller를 수정했는가?
```

REST API URL 버전과 proto package 버전은 별개다. 현재는 둘 다 v1을 사용하지만, 하나가 바뀐다고 다른 하나를 자동으로 올리지 않는다.

---

## 27. API 변경 체크리스트

API를 추가/수정할 때 확인한다.

```text
URL이 /api/{domain}/v1/{resource} 패턴인가?
동사가 불필요하게 endpoint에 들어가지 않았는가?
인증 필요 여부가 명확한가?
request/response DTO가 명확한가?
실패 응답 code가 정의되어 있는가?
cursor pagination이 필요한 목록 API인가?
동시성 민감 API라면 idempotencyKey가 있는가?
API 명세서도 함께 수정해야 하는가?
```

업적/광고 API는 현재 만들지 않는다.

---

## 28. DB/ERD 변경 체크리스트

DB 변경은 신중하게 한다.

```text
최신 ERD에 있는 테이블인가?
MVP 제외 기능 테이블은 아닌가?
unique/check/index가 필요한가?
동시성 제어가 필요한가?
nullable 정책이 명확한가?
기본값이 필요한가?
삭제/soft delete 정책이 필요한가?
```

unique index를 추가하는 migration은 기존 데이터 중복 여부를 먼저 점검한다. 특히 기존 값을 정규화한 직후 unique index를 거는 경우에는 "정규화 후 값" 기준으로 중복 0건을 확인하는 쿼리를 배포 체크리스트에 남긴다.

예: character `share_cards`는 V10에서 `image_url` full URL을 object key로 정규화하고 V12에서 `(user_id, image_url)` partial unique index를 생성한다. V12 적용 전 아래 결과가 0건이어야 한다.

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

다음 테이블/기능은 사용자가 다시 요청하기 전까지 만들지 않는다.

```text
achievements
user_achievements
ad_events
payment_orders
payment_transactions
mock_star_piece_purchases
gacha_pools
gacha_results
```

---

## 29. 금지되는 LLM 행동

다음 행동은 금지한다.

```text
파일을 제대로 읽지 않고 코드 생성
존재하지 않는 라이브러리/메서드/어노테이션 사용
build.gradle 임의 수정
application.yaml에 secret 추가
도메인 경계 무시
Repository를 다른 서비스에서 직접 호출
DTO 없이 Entity 반환
미션/별조각 보상 중복 가능성 방치
N+1 가능성 무시
요청과 무관한 코드 스타일 변경
테스트 실패를 숨김
실행하지 않은 테스트를 실행했다고 말함
```

---

## 30. 보고해야 하는 문제

다음은 직접 고치지 말고 보고한다.

```text
문서와 코드의 API 경로 불일치
ERD와 Entity 불일치
상태 enum 이름 불일치
Spring Boot 버전 불일치
이미 존재하는 사용되지 않는 코드
순환 의존 가능성
보안상 위험한 기존 설정
빌드 산출물이 저장소에 포함된 상황
```

보고 형식:

```text
발견한 문제:
- ...

영향:
- ...

추천 조치:
- ...

이번 작업에서 처리 여부:
- 처리하지 않음 / 사용자 확인 필요
```

---

## 31. PR 전 자체 점검

PR을 만들기 전에 다음을 확인한다.

```text
요구사항 외 변경 없음
MVP 제외 기능 추가 없음
API 명세와 URL 일치
ERD와 테이블/필드 일치
도메인 경계 유지
gateway와 gRPC 역할 분리
트랜잭션 경계 적절
멱등성 필요한 API 확인
소유권 검증 확인
테스트 실행 결과 정리
```

PR 설명에는 다음을 포함한다.

```text
## 작업 내용
- ...

## 검증
- [ ] ./gradlew :module:test
- [ ] 수동 확인 내용

## 주의 사항
- ...
```

---

## 32. 에이전트 응답 원칙

코딩 작업 중 사용자에게 보고할 때는 간결하지만 숨기지 않는다.

반드시 말할 것:

```text
확실하지 않은 점
가정한 점
선택지와 장단점
실행한 검증
실행하지 못한 검증과 이유
```

하지 말 것:

```text
모르는 것을 아는 척하기
테스트 실행 여부를 속이기
긴 설명으로 불확실성 숨기기
요청하지 않은 대규모 리팩토링 제안으로 흐름 바꾸기
```

---

## 33. 핵심 원칙 요약

```text
생각하고 코딩한다.
추측하지 않는다.
작게 바꾼다.
단순하게 만든다.
검증 가능한 목표로 작업한다.
도메인 경계를 지킨다.
MVP 제외 기능은 만들지 않는다.
별조각/미션/아이템/공유/출석의 정합성을 최우선으로 본다.
테스트와 실행 결과를 솔직하게 보고한다.
```
