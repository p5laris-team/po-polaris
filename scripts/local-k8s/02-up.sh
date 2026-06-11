#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

NAMESPACE="p5laris-lab"
LOCAL_CONTEXT="docker-desktop"

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

echo "== p5laris 로컬 Kubernetes 실행을 시작합니다 =="

echo
echo "== kubectl context를 docker-desktop으로 전환합니다 =="
kubectl config use-context "$LOCAL_CONTEXT"

echo
echo "== 로컬 이미지 존재 확인 =="
for svc in "${SERVICES[@]}"; do
  image="p5laris-${svc}:local"
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "OK: $image"
  else
    echo "ERROR: $image 이미지가 없습니다."
    echo "먼저 다음 명령을 실행하세요:"
    echo "./scripts/local-k8s/01-build-images.sh"
    exit 1
  fi
done

echo
echo "== Kubernetes manifest 렌더링 확인 =="
kubectl kustomize k8s/overlays/local >/tmp/p5laris-local-rendered.yaml
echo "OK: manifest 렌더링 성공"

echo
echo "== Kubernetes 리소스 적용 =="
kubectl apply -k k8s/overlays/local

echo
echo "== Deployment rollout 확인 =="
for svc in "${SERVICES[@]}"; do
  deploy="p5laris-${svc}"
  echo
  echo "대기 중: $deploy"
  if kubectl -n "$NAMESPACE" rollout status "deploy/$deploy" --timeout=420s; then
    echo "OK: $deploy rollout 완료"
  else
    echo "ERROR: $deploy rollout 실패"
    echo "상태 확인:"
    kubectl -n "$NAMESPACE" get pods -o wide
    echo
    echo "이벤트 확인:"
    kubectl -n "$NAMESPACE" get events --sort-by=.lastTimestamp | tail -80
    exit 1
  fi
done

echo
echo "== 현재 Pod 상태 =="
kubectl -n "$NAMESPACE" get pods -o wide

echo
echo "== 로컬 Kubernetes 실행 완료 =="
echo "다음 명령으로 검증하세요:"
echo "./scripts/local-k8s/03-verify.sh"
