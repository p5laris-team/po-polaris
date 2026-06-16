<img src="docs/images/polaris-banner.png" width="100%" alt="Polaris Banner">
  <br /><br />

<div align="center">
  <h1 style="border-bottom: none; font-size: 2.5em; font-weight: bold;">
    <img src="docs/images/logomark.png" width="50" alt="Logo" style="vertical-align: middle; margin-right: 10px;"> Polaris
  </h1>
  <p style="color: #8b949e; font-size: 1.2em; letter-spacing: 2px;">
    <b>AI CHARACTER ROUTINE MAKER</b>
  </p>
  <br />
  <hr style="background: linear-gradient(to right, transparent, #30363d, transparent); height: 1px; border: none;" />
  <br />
</div>

<p align="center">
  <img src="https://img.shields.io/badge/Java%2021-ED8B00?style=flat-square&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring%20Boot%203.x-6DB33F?style=flat-square&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/gRPC-244C5A?style=flat-square&logo=grpc&logoColor=white">
  <br>
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/pgvector-336791?style=flat-square&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white">
  <img src="https://img.shields.io/badge/Google%20Gemini-4285F4?style=flat-square&logo=google&logoColor=white">
  <br>
  <img src="https://img.shields.io/badge/Nx%20Monorepo-143055?style=flat-square&logo=nx&logoColor=white">
  <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white">
</p>

---

## 📌 프로젝트 소개

**Polaris**는 AI 캐릭터와 상호작용하며 건강한 일상 루틴을 만들어가는 **차세대 루틴 메이커 서비스**입니다.  
단순한 체크리스트를 넘어, **맞춤형 동적 미션 생성 및 보상 트랜잭션 검증**을 통해 확장성 높고 안정적인 마이크로서비스(MSA)를 지향합니다.

---

#### 🤖 AI 기반 동적 미션 (AI-Driven Missions)
* **상황 맞춤 제안:** Google Gemini를 활용해 유저의 시간/장소 컨텍스트에 맞는 새로운 루틴 미션 생성
* **피드백 학습:** 거절 사유를 수집하여 다음 번 미션 제안의 정확도와 개인화 수준 향상
* **대화형 완료:** 단순 클릭 완료가 아닌 AI와의 질문/답변 세션을 통한 성취감 제공

#### 👾 캐릭터 및 상호작용 (Character & Interaction)
* **실시간 딥톡:** SSE(Server-Sent Events)를 활용한 지연 없는 캐릭터 AI 스트리밍 대화
* **장기 기억 장착:** pgvector를 활용해 과거 대화를 벡터로 저장 및 유사도 검색(RAG) 적용
* **성장 시스템:** 밥 주기, 재우기 등 돌봄 액션을 통한 상태(포만감/에너지/애정도) 및 레벨 관리

#### 💎 별조각 경제 및 보상 (Star Piece Economy)
* **보상 트랜잭션:** Outbox Pattern과 멱등키를 활용하여 네트워크 장애 시에도 중복 지급 없는 안전한 재화(별조각) 관리
* **아이템 상점:** 획득한 별조각으로 돌봄 아이템 구매 및 인벤토리 관리
* **커스텀 스킨:** 캐릭터 외형을 꾸미고 뷰티 스킨 적용 가능

---

## 👥 팀소개

| 이름  | 역할 | 담당                                                          |
|-----|----|-------------------------------------------------------------|
| 김소현 | 팀장 | 인증 인가, 온보딩, 상점 경제 시스템, 동시성/멱등성 제어, 모니터링                     |
| 성기찬 | 팀원 | MSA 인프라 구축, Gateway 라우팅, gRPC 공통 모듈, CI/CD 자동화, 알림 시스템(FCM) |
| 박현지 | 팀원 | Gemini 프롬프트 엔지니어링, SSE 스트리밍, 미션 상태 머신                       |
| 강태훈 | 팀원 | 캐릭터 관리, 캐릭터 카드 공유, 배너 광고                                    |

<br>

### [📎프로젝트 브로셔 바로가기](https://www.notion.so/)

---

## ⏲️ 개발기간
- 2026.05.12(화) ~ 2026.06.22(월)

---

## 🧩 Architecture

<p align="center">
  <img src="" width="100%" alt="Architecture">
</p>

---

## 🔧 Technologies & Tools

#### 🖥️ Backend Stack
<p align="left">
  <img src="https://img.shields.io/badge/Java%2021-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white">
  <img src="https://img.shields.io/badge/Spring%20Boot%203.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/Spring%20Data%20JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white">
  <img src="https://img.shields.io/badge/gRPC-244C5A?style=for-the-badge&logo=grpc&logoColor=white">
</p>

#### 💾 Data & Infrastructure
<p align="left">
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/pgvector-336791?style=for-the-badge&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">
  <img src="https://img.shields.io/badge/AWS%20S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white">
</p>

#### 🤖 AI & External API
<p align="left">
  <img src="https://img.shields.io/badge/Google%20Gemini-4285F4?style=for-the-badge&logo=google&logoColor=white">
  <img src="https://img.shields.io/badge/FCM-FFCA28?style=for-the-badge&logo=firebase&logoColor=white">
</p>

#### 🧪 Quality & DevOps
<p align="left">
  <img src="https://img.shields.io/badge/Nx%20Monorepo-143055?style=for-the-badge&logo=nx&logoColor=white">
  <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white">
  <img src="https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white">
</p>

---

## 🧠 적용 기술

#### ◻ **MSA & gRPC 통신**
> 단일 서버의 AI 스레드 점유로 인한 병목을 해소하기 위해 8개의 마이크로서비스로 분리하였으며, 내부 통신은 REST 대신 HTTP/2 기반의 gRPC를 채택하여 고속 바이너리 직렬화를 구현했습니다.

#### ◻ **보상 트랜잭션 (Outbox Pattern)**
> 미션 완료 시 별조각 보상 및 경험치 지급 과정에서 네트워크 장애가 발생하더라도 데이터가 유실되지 않도록 이벤트 테이블을 활용한 Outbox Pattern과 `Idempotency-Key` 검증을 적용했습니다.

#### ◻ **SSE (Server-Sent Events) 스트리밍**
> LLM 텍스트 생성의 높은 지연율을 해결하기 위해 캐릭터 AI 응답을 단방향 스트리밍(SSE)으로 클라이언트에 즉각 푸시하여 실시간성을 극대화했습니다.

#### ◻ **벡터 유사도 검색 (pgvector)**
> 사용자와의 대화 내역을 요약 및 임베딩 벡터로 변환하여 PostgreSQL에 저장하고, 다음 대화 시 코사인 유사도 기반으로 과거 기억을 검색(RAG)해 페르소나의 일관성을 유지합니다.

#### ◻ **JWT & Redis**
> API Gateway 수준에서 JWT 토큰을 일괄 검증하며, 로그아웃 시 Redis 블랙리스트를 활용하여 세션을 안전하게 파기하고 보안을 강화했습니다.

#### ◻ **Spring Event & 비동기 처리**
> 푸시 알림 발송이나 이벤트 로그 적재 등 메인 트랜잭션과 생명주기가 달라도 되는 서브 작업들은 `Spring ApplicationEventPublisher`를 활용해 관심사를 분리하고 비동기로 처리하여 API 응답 속도를 개선했습니다.

#### ◻ **Flyway (DB Migration)**
> 마이크로서비스별 독립적인 데이터베이스 스키마(PostgreSQL)의 형상 관리를 위해 Flyway를 도입하여, CI/CD 파이프라인에서 스크립트 기반 자동 마이그레이션이 이루어지도록 구축했습니다.

---

## 🚀 주요 기능

#### 🔐 인증 및 보안
- **소셜 로그인:** Google OAuth 2.0 연동을 통한 가입 및 토큰 기반(JWT) 인증
- **중앙화 인가:** API Gateway의 `AuthInterceptor`를 통한 라우팅 전 선행 인가 처리

#### 👾 캐릭터 육성
- **다중 타입 지원:** NOVA, MUMU, JJORY 중 유저 성향에 맞는 캐릭터 선택 가능
- **상태 머신:** 먹이, 휴식 등 돌봄 상호작용에 따른 포만감 및 애정도 실시간 증감 시스템

#### 🎯 동적 미션 체계
- **온보딩 연동:** 초기 라이프스타일 설문에 기반한 미션 난이도 및 주제 자동 배정
- **상태 관리:** 미션 제안, 수락, 거절, 응답 제출 및 완료에 이르는 라이프사이클 처리

#### 💳 상점 및 경제
- **별조각 순환:** 획득(미션, 공유, 출석)부터 소비(아이템 및 스킨 구매)까지의 재화 생태계
- **멱등성 구매:** 중복 결제 방지를 위한 결제 검증 로직 구현

#### 📣 푸시 및 공유
- **이미지 생성:** S3 Presigned URL을 발급받아 미션 완료 증명 카드를 SNS로 렌더링 후 공유
- **스마트 알림:** 방해금지 시간 설정에 연동되는 FCM 기반 맞춤형 푸시 메시지 발송

### 🌦️ 개인화 및 컨텍스트 (Personalization)
- **날씨/지역 연동:** 유저가 설정한 날씨 권역 정보(`WeatherRegionCode`)를 바탕으로, 비가 오는 날엔 실내 활동을 제안하는 등 환경 맞춤형 특수 루틴 제안

### 📜 행동 분석 로깅 (Event Logging)
- **통합 로그 적재:** 미션 완료, 상점 구매 등 유저의 주요 행동 이벤트를 도메인 로직과 분리하여 비동기로 수집하고, `event-log` 모듈로 적재하여 향후 유저 리텐션 분석 및 지표 추출 기반 마련

---

## 🗺 User Flow

<p align="center">
  <img src="docs/images/polaris_user_flow.png" width="80%" alt="Polaris User Flow">
</p>

---

## 🖼 API 명세서

<p align="center">
  <img src="docs/images/API.png" width="100%" alt="API 명세서">
</p>

보다 자세한 API 명세서는
[📎API Spec](docs/sa-docs/01-API-spec.md) 에서 확인할 수 있습니다.

---

## 🗄 ERD Diagram

<p align="center">
  <img src="docs/images/ERD.png" width="100%" alt="ERD">
</p>

보다 자세한 ERD는
[📎ERD Data Model](docs/sa-docs/02_ERD_Data_Model.md) 에서 확인할 수 있습니다.

---

## 📈 프로젝트 파일 구조

```text
src/
├── 📂 gateway              # REST API 진입점, JWT 글로벌 검증, gRPC 클라이언트 라우팅
├── 📂 user                 # 회원 프로필, 온보딩, 지갑(별조각), 출석 기록 관리
│   ├── 📂 core             # 인증/인가 인터페이스 및 공통 비즈니스 예외 처리
│   ├── 📂 domain           # 비즈니스 핵심 영역 (엔티티, 애플리케이션 로직)
│   │   ├── 📂 api          # 외부에서 호출하는 gRPC Controller 엔드포인트
│   │   ├── 📂 application  # 서비스 로직 (Auth, Attendance, Wallet 등) 및 Event Listener
│   │   ├── 📂 entity       # JPA 엔티티 (User, Wallet, OutboxEvent 등)
│   │   └── 📂 repository   # 데이터 베이스 접근 계층 (Spring Data JPA)
│   ├── 📂 infrastructure   # Google OAuth 연동 등 외부 API 구체 구현체
│   └── 📂 resources        # application.yaml 및 Flyway DB 마이그레이션 스크립트
├── 📂 character            # 캐릭터 상태, 돌봄 액션 기록, 스킨 장착, SNS 공유 정보
├── 📂 mission              # 유저의 데일리 미션 상태 머신 및 완료 트랜잭션 관리
├── 📂 item                 # 상점 인벤토리 및 소모성 아이템 사용 로직
├── 📂 ai                   # Google Gemini 연동, 미션 프롬프트, 챗 스트리밍, 벡터 임베딩
├── 📂 notification         # FCM 토큰 발급 및 시스템/개별 푸시 알림 발송
├── 📂 event-log            # 로그 통합 적재 서버 (Elasticsearch 연동 등 대비)
├── 📂 proto                # MSA 간 통신을 위한 Protocol Buffers 인터페이스 정의
└── 📂 common               # 공통 Error Handler, Response DTO, 유틸리티 함수
```

---
