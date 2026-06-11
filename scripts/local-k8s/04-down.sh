#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

NAMESPACE="p5laris-lab"
LOCAL_CONTEXT="docker-desktop"

echo "== p5laris 로컬 Kubernetes 리소스를 종료합니다 =="

echo
echo "== kubectl context를 docker-desktop으로 전환합니다 =="
kubectl config use-context "$LOCAL_CONTEXT"

echo
echo "== Kubernetes 리소스 삭제 =="
kubectl delete -k k8s/overlays/local --ignore-not-found=true

echo
echo "== 남은 리소스 확인 =="
kubectl -n "$NAMESPACE" get all || true

echo
echo "== 종료 완료 =="
echo "주의: 팀 공통 PostgreSQL/Redis Docker Compose는 종료하지 않았습니다."
