#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

CONTEXT="docker-desktop"
NAMESPACE="p5laris-lab"
OVERLAY_DIR="k8s/overlays/local"

echo "== p5laris 로컬 Kubernetes 사전 점검을 시작합니다 =="

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: '$1' 명령어를 찾을 수 없습니다."
    exit 1
  fi
}

echo
echo "== 필수 명령어 확인 =="
need_cmd docker
need_cmd kubectl
need_cmd curl
echo "OK: docker / kubectl / curl"

echo
echo "== Docker 실행 상태 확인 =="
docker info >/dev/null
echo "OK: Docker가 실행 중입니다."

echo
echo "== kubectl context 확인 =="
kubectl config use-context "$CONTEXT" >/dev/null
CURRENT_CONTEXT="$(kubectl config current-context)"
echo "current-context: $CURRENT_CONTEXT"

if [ "$CURRENT_CONTEXT" != "$CONTEXT" ]; then
  echo "ERROR: kubectl context가 $CONTEXT 가 아닙니다."
  exit 1
fi

echo
echo "== Kubernetes node 상태 확인 =="
kubectl get nodes -o wide

echo
echo "== kustomize 렌더링 확인 =="
TMP_RENDERED="$(mktemp)"
kubectl kustomize "$OVERLAY_DIR" > "$TMP_RENDERED"
echo "OK: kubectl kustomize $OVERLAY_DIR"

echo
echo "== 렌더링된 주요 설정 확인 =="
grep -nE 'KAFKA_BOOTSTRAP_SERVERS|REDIS_HOST|REDIS_PORT|DB_URL|GRPC_SERVER_PORT' "$TMP_RENDERED" | head -120 || true

KAFKA_BOOTSTRAP_SERVERS="$(
  awk '/KAFKA_BOOTSTRAP_SERVERS:/ {print $2; exit}' "$TMP_RENDERED" | tr -d '"'
)"

echo
echo "== PostgreSQL 접속 확인 =="
docker run --rm postgres:16-alpine \
  pg_isready -h host.docker.internal -p 5432
echo "OK: PostgreSQL host.docker.internal:5432 접속 가능"

echo
echo "== Redis 접속 확인 =="
REDIS_PING="$(docker run --rm redis:8-alpine \
  redis-cli -h host.docker.internal -p 6379 ping)"
echo "$REDIS_PING"

if [ "$REDIS_PING" != "PONG" ]; then
  echo "ERROR: Redis PING 응답이 PONG이 아닙니다."
  exit 1
fi

echo "OK: Redis host.docker.internal:6379 접속 가능"

echo
echo "== Kafka 접속 확인 =="
if [ -z "$KAFKA_BOOTSTRAP_SERVERS" ]; then
  echo "ERROR: k8s overlay에서 KAFKA_BOOTSTRAP_SERVERS를 찾지 못했습니다."
  exit 1
fi

echo "KAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS"

if ! docker ps --format '{{.Names}}' | grep -qx 'kafka-broker'; then
  echo "ERROR: kafka-broker 컨테이너가 실행 중이 아닙니다."
  echo "예: docker compose -f docker-compose-kafka.yml up -d"
  exit 1
fi

KAFKA_PORT="${KAFKA_BOOTSTRAP_SERVERS##*:}"

docker exec kafka-broker kafka-topics \
  --bootstrap-server "localhost:${KAFKA_PORT}" \
  --list >/tmp/p5laris-kafka-topics.log

echo "OK: kafka-broker localhost:${KAFKA_PORT} 접속 가능"

ADVERTISED_LISTENERS="$(docker exec kafka-broker printenv KAFKA_ADVERTISED_LISTENERS || true)"
echo "KAFKA_ADVERTISED_LISTENERS=$ADVERTISED_LISTENERS"

if ! echo "$ADVERTISED_LISTENERS" | grep -q "$KAFKA_BOOTSTRAP_SERVERS"; then
  echo "WARN: Kafka advertised listeners에 $KAFKA_BOOTSTRAP_SERVERS 가 보이지 않습니다."
  echo "Docker Desktop Kubernetes Pod에서 Kafka metadata 재접속 문제가 날 수 있습니다."
fi

echo
echo "== Kubernetes Pod에서 Kafka 접근 확인 =="
CHECK_POD="kafka-prereq-check-$(date +%s)"

kubectl run "$CHECK_POD" \
  --rm -i \
  --restart=Never \
  --image=confluentinc/cp-kafka:7.5.0 \
  -- kafka-topics --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" --list >/tmp/p5laris-kafka-k8s-check.log

echo "OK: Kubernetes Pod에서 Kafka $KAFKA_BOOTSTRAP_SERVERS 접근 가능"

echo
echo "== 로컬 Docker 이미지 확인 =="
for svc in user character item mission event-log ai notification gateway; do
  if docker image inspect "p5laris-${svc}:local" >/dev/null 2>&1; then
    echo "OK: p5laris-${svc}:local"
  else
    echo "WARN: p5laris-${svc}:local 이미지가 없습니다. 필요 시 ./scripts/local-k8s/01-build-images.sh $svc 실행"
  fi
done

rm -f "$TMP_RENDERED"

echo
echo "== 사전 점검 완료 =="
