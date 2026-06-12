# JaCoCo Coverage Baseline

측정일: 2026-06-12

이 기준선은 핵심 결제 단위 테스트, user Testcontainers 통합 테스트, 미션
보상·시간 정책 테스트, 외부 인프라 없이 실행 가능한 AI application·policy
테스트를 사용해 측정했다.

## 현재 결과

| 범위 | 라인 커버리지 |
|---|---:|
| 애플리케이션 모듈 전체 | 8.00% |
| `PaymentService` | 23.1% |
| `MissionRewardDispatcher` | 31.8% |
| `MissionTimePolicy` | 66.1% |
| `AiCharacterTalkService` | 91.9% |
| `AiMissionTextPersistenceService` | 100% |
| `AiTextEmbeddingService` | 80.6% |
| `CharacterTalkValidationPolicy` | 80.0% |
| `MissionTextValidationPolicy` | 89.3% |

전체 수치는 controller, config, entity와 아직 테스트가 없는 모듈도 포함한
현황 지표다. 해당 코드는 리포트에서 숨기지 않지만 현재 PR 차단 기준으로
사용하지 않는다.

## 검증 단계

- `jacocoBaselineVerification`: 현재 핵심 경로의 커버리지 하락을 차단한다.
- `jacocoTargetVerification`: 전체 60%, 핵심 서비스 70%, policy 80% 목표를
  검사한다. 현재는 목표 미달 상태를 확인하는 수동 task다.

```powershell
.\gradlew.bat jacocoRootReport
.\gradlew.bat jacocoBaselineVerification
.\gradlew.bat jacocoTargetVerification
```

HTML 리포트는 `build/reports/jacoco/root/html/index.html`에 생성된다.

## 다음 보강 순서

1. `PaymentService` 승인 실패, 검증 예외, 취소·환불 상태 전이
2. `MissionRewardDispatcher` 재시도 한도, backoff, poison event, 복구
3. `MissionTimePolicy` 시간대 경계값과 카테고리 조합
4. 로컬 DB에 의존하는 기존 Spring context 테스트의 Testcontainers 격리
5. 미검증 모듈의 핵심 application service 테스트
