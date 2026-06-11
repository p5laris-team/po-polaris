#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="p5laris-lab"

if [ $# -lt 1 ]; then
  echo "사용법: $0 <service> [tail_lines] [--previous]"
  echo "예: $0 user"
  echo "예: $0 gateway 500"
  echo "예: $0 ai 500 --previous"
  exit 1
fi

SERVICE="$1"
TAIL_LINES="${2:-300}"
PREVIOUS="${3:-}"

if [[ "$TAIL_LINES" == --previous ]]; then
  PREVIOUS="--previous"
  TAIL_LINES="300"
fi

latest_running_pod() {
  kubectl -n "$NAMESPACE" get pods \
    -l "app=p5laris-${SERVICE}" \
    --field-selector=status.phase=Running \
    --sort-by=.metadata.creationTimestamp \
    -o name 2>/dev/null \
    | tail -n 1 \
    | sed 's#pod/##'
}

latest_any_pod() {
  kubectl -n "$NAMESPACE" get pods \
    -l "app=p5laris-${SERVICE}" \
    --sort-by=.metadata.creationTimestamp \
    -o name 2>/dev/null \
    | tail -n 1 \
    | sed 's#pod/##'
}

echo "== p5laris-${SERVICE} 로그를 확인합니다 =="
echo "namespace: $NAMESPACE"
echo "tail: $TAIL_LINES"

POD="$(latest_running_pod)"

if [ -z "$POD" ]; then
  POD="$(latest_any_pod)"
  if [ -z "$POD" ]; then
    echo "ERROR: p5laris-${SERVICE} Pod를 찾지 못했습니다."
    exit 1
  fi

  echo "WARN: Running Pod가 없어 최신 Pod를 사용합니다: $POD"
  echo "상태:"
  kubectl -n "$NAMESPACE" get pod "$POD"

  if [ -z "$PREVIOUS" ]; then
    echo
    echo "== 현재 로그 =="
    kubectl -n "$NAMESPACE" logs "$POD" --tail="$TAIL_LINES" || true

    echo
    echo "== previous 로그 =="
    kubectl -n "$NAMESPACE" logs "$POD" --previous --tail="$TAIL_LINES" || true
    exit 0
  fi
fi

echo "pod: $POD"

if [ "$PREVIOUS" = "--previous" ]; then
  kubectl -n "$NAMESPACE" logs "$POD" --previous --tail="$TAIL_LINES"
else
  kubectl -n "$NAMESPACE" logs "$POD" --tail="$TAIL_LINES"
fi
