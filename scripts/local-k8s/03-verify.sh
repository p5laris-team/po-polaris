#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

NAMESPACE="p5laris-lab"
CONTEXT="docker-desktop"

SERVICES=(user character item mission event-log ai notification gateway)
KAFKA_SERVICES=(user character item mission event-log ai notification)

declare -A GRPC_PORTS=(
  [user]=9091
  [character]=9092
  [item]=9093
  [mission]=9094
  [ai]=9095
  [notification]=9098
  [event-log]=9099
)

SKIP_SERVICES="${SKIP_SERVICES:-}"

for arg in "$@"; do
  case "$arg" in
    --skip=*)
      SKIP_SERVICES="$SKIP_SERVICES ${arg#--skip=}"
      ;;
    --skip)
      echo "ERROR: --skip=ai 형식으로 사용하세요."
      exit 1
      ;;
    *)
      SKIP_SERVICES="$SKIP_SERVICES $arg"
      ;;
  esac
done

is_skipped() {
  local svc="$1"
  for skipped in $SKIP_SERVICES; do
    if [ "$svc" = "$skipped" ]; then
      return 0
    fi
  done
  return 1
}

is_kafka_service() {
  local svc="$1"
  for kafka_svc in "${KAFKA_SERVICES[@]}"; do
    if [ "$svc" = "$kafka_svc" ]; then
      return 0
    fi
  done
  return 1
}

latest_running_pod() {
  local svc="$1"
  kubectl -n "$NAMESPACE" get pods \
    -l "app=p5laris-${svc}" \
    --field-selector=status.phase=Running \
    --sort-by=.metadata.creationTimestamp \
    -o name 2>/dev/null \
    | tail -n 1 \
    | sed 's#pod/##'
}

latest_any_pod() {
  local svc="$1"
  kubectl -n "$NAMESPACE" get pods \
    -l "app=p5laris-${svc}" \
    --sort-by=.metadata.creationTimestamp \
    -o name 2>/dev/null \
    | tail -n 1 \
    | sed 's#pod/##'
}

run_curl_check() {
  local svc="$1"
  local check_pod="verify-curl-${svc}-$(date +%s)"

  kubectl -n "$NAMESPACE" run "$check_pod" \
    --rm -i \
    --restart=Never \
    --labels=app=p5laris-verify-tmp \
    --image=curlimages/curl:8.11.1 \
    -- sh -c "curl -fsS http://${svc}-service:8080/actuator/health"
}

run_grpc_check() {
  local svc="$1"
  local port="$2"
  local check_pod="verify-grpc-${svc}-$(date +%s)"

  kubectl -n "$NAMESPACE" run "$check_pod" \
    --rm -i \
    --restart=Never \
    --labels=app=p5laris-verify-tmp \
    --image=fullstorydev/grpcurl:v1.9.3 \
    -- -plaintext "${svc}-service:${port}" grpc.health.v1.Health/Check
}

FAIL=0

echo "== p5laris 로컬 Kubernetes 검증을 시작합니다 =="

echo
echo "== kubectl context 확인 =="
kubectl config use-context "$CONTEXT" >/dev/null
kubectl config current-context

echo
echo "== 이전 임시 검증 Pod 정리 =="
kubectl -n "$NAMESPACE" delete pod -l app=p5laris-verify-tmp --ignore-not-found=true >/dev/null 2>&1 || true
echo "OK"

echo
echo "== Pod 상태 확인 =="
kubectl -n "$NAMESPACE" get pods -o wide || {
  echo "ERROR: namespace 또는 Pod 조회 실패"
  exit 1
}

echo
echo "== Spring 기동 로그 확인 =="
for svc in "${SERVICES[@]}"; do
  if is_skipped "$svc"; then
    echo
    echo "===== [p5laris-${svc}] SKIP ====="
    continue
  fi

  echo
  echo "===== [p5laris-${svc}] 로그 핵심 확인 ====="

  POD="$(latest_running_pod "$svc")"

  if [ -z "$POD" ]; then
    POD="$(latest_any_pod "$svc")"
    echo "ERROR: p5laris-${svc} Running Pod가 없습니다. latest pod=$POD"
    if [ -n "$POD" ]; then
      kubectl -n "$NAMESPACE" logs "$POD" --previous --tail=120 || \
      kubectl -n "$NAMESPACE" logs "$POD" --tail=120 || true
    fi
    FAIL=1
    continue
  fi

  echo "pod: $POD"

  LOG_TAIL="$(kubectl -n "$NAMESPACE" logs "$POD" --tail=400 || true)"

  echo "$LOG_TAIL" | grep -E \
    'Started .*Application|Tomcat started|gRPC Server started|Application run failed|Exception encountered during context initialization|Could not resolve placeholder|No qualifying bean|UnsatisfiedDependencyException|BeanCreationException' \
    || true

  if echo "$LOG_TAIL" | grep -qE 'Application run failed|Could not resolve placeholder|No qualifying bean|UnsatisfiedDependencyException|BeanCreationException'; then
    echo "ERROR: p5laris-${svc} Spring 기동 실패 로그가 있습니다."
    FAIL=1
    continue
  fi

  if echo "$LOG_TAIL" | grep -qE 'Started .*Application'; then
    echo "OK: p5laris-${svc} Spring Application 시작 확인"
  else
    echo "WARN: p5laris-${svc} 로그 tail 안에서 Started Application 문구를 찾지 못했습니다."
  fi

  if is_kafka_service "$svc"; then
    echo "Kafka env:"
    kubectl -n "$NAMESPACE" exec "$POD" -- printenv KAFKA_BOOTSTRAP_SERVERS || true
  fi
done

echo
echo "== HTTP health 확인 =="
for svc in "${SERVICES[@]}"; do
  if is_skipped "$svc"; then
    echo "SKIP: ${svc} HTTP health"
    continue
  fi

  echo
  echo "===== [${svc}] HTTP /actuator/health ====="
  if run_curl_check "$svc"; then
    echo
    echo "OK: ${svc} HTTP health"
  else
    echo
    echo "ERROR: ${svc} HTTP health 실패"
    FAIL=1
  fi
done

echo
echo "== gRPC health 확인 =="
for svc in "${!GRPC_PORTS[@]}"; do
  if is_skipped "$svc"; then
    echo "SKIP: ${svc} gRPC health"
    continue
  fi

  echo
  echo "===== [${svc}] gRPC health ====="
  if run_grpc_check "$svc" "${GRPC_PORTS[$svc]}"; then
    echo "OK: ${svc} gRPC health"
  else
    echo "ERROR: ${svc} gRPC health 실패"
    FAIL=1
  fi
done

echo
echo "== 검증 결과 =="
if [ "$FAIL" -eq 0 ]; then
  echo "OK: 전체 검증 통과"
else
  echo "ERROR: 일부 검증 실패"
fi

exit "$FAIL"
