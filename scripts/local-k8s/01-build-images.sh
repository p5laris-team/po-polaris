#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

SERVICES=(
  user
  character
  item
  mission
  event-log
  ai
  notification
  gateway
)

if [ "$#" -gt 0 ]; then
  SERVICES=("$@")
fi

echo "== p5laris 로컬 Docker 이미지 빌드를 시작합니다 =="
echo "대상 서비스: ${SERVICES[*]}"
echo

for svc in "${SERVICES[@]}"; do
  echo
  echo "===== [$svc] bootJar 빌드 시작 ====="

  if [ ! -d "$svc" ]; then
    echo "ERROR: $svc 디렉터리가 없습니다."
    exit 1
  fi

  ./gradlew ":${svc}:bootJar"

  JAR_FILE="$(find "$svc/build/libs" \
    -maxdepth 1 \
    -type f \
    -name "*.jar" \
    ! -name "*plain.jar" \
    | head -n 1)"

  if [ -z "$JAR_FILE" ]; then
    echo "ERROR: $svc bootJar 결과물을 찾지 못했습니다."
    echo "확인 경로: $svc/build/libs"
    exit 1
  fi

  IMAGE_NAME="p5laris-${svc}:local"

  echo "JAR: $JAR_FILE"
  echo "IMAGE: $IMAGE_NAME"
  echo "===== [$svc] Docker 이미지 빌드 시작 ====="

  docker build \
    --build-arg JAR_FILE="$JAR_FILE" \
    -t "$IMAGE_NAME" \
    .

  if docker image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
    echo "OK: $IMAGE_NAME 이미지 생성 완료"
  else
    echo "ERROR: $IMAGE_NAME 이미지 확인 실패"
    exit 1
  fi
done

echo
echo "== 모든 로컬 이미지 빌드가 완료되었습니다 =="
docker images 'p5laris-*' --format 'table {{.Repository}}\t{{.Tag}}\t{{.Size}}'
