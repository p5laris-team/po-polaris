# 리뷰와 품질 게이트

이 저장소는 자동 검증을 필수 품질 신호로 사용하고 CodeRabbit을 보조
리뷰어로 사용한다. AI 리뷰 통과만으로 테스트를 대체하지 않는다.

## PR 필수 검사

| 검사 | 검증 범위 |
|---|---|
| Critical Gradle Tests | 전체 test source 컴파일, 핵심 단위·Testcontainers 통합 테스트, JaCoCo 기준선 |
| Contract Documentation | gateway REST Docs 생성 가능 여부 |
| Load And Chaos Smoke | synthetic data, AI mock 장애, k6 timeout 및 복구 |

GitHub `main` 브랜치 보호 규칙에서 위 세 check를 required status check로
지정해야 merge 차단이 실제 적용된다.

## 리뷰 우선순위

1. MSA 경계와 REST/gRPC/protobuf 계약 호환성
2. 결제, 지갑, 보상, 상태 전이의 정합성
3. 멱등성 키와 Kafka 중복 전달, retry, DLT
4. timeout, fallback, rollback 등 실패 경로
5. `traceId`, `eventId`, outbox 상태 등 관측 가능성
6. secret, 개인정보, 사용자 원문 노출

## CodeRabbit 역할

`.coderabbit.yaml`은 경로별 리뷰 기준을 제공한다. CodeRabbit 지적은
근거를 검토해 반영하거나 PR 댓글에 미반영 사유를 기록한다. 자동 테스트
실패는 AI 리뷰 결과와 관계없이 수정해야 한다.

## 로컬 재현

```powershell
.\gradlew.bat testClasses
.\gradlew.bat :user:test --tests "*PaymentServiceIdempotencyTest" --tests "*PaymentStateTransitionTest"
.\gradlew.bat :mission:test --tests "*MissionRewardDispatcherTest"
.\gradlew.bat jacocoBaselineVerification
.\gradlew.bat :user:test --tests "p5laris.user.integration.*"
.\gradlew.bat :gateway:asciidoctor
npm.cmd run simulation:data
npm.cmd run simulation:validate
npm.cmd run simulation:smoke
```

현재 일부 레거시 `contextLoads` 테스트는 로컬 PostgreSQL 설정에 의존하므로
전체 `test` task는 필수 PR check에 포함하지 않는다. 해당 테스트를
Testcontainers로 격리한 뒤 전체 suite를 required check로 승격한다.
