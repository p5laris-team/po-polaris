#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:-}"

if [ -z "$SERVICE" ]; then
  echo "Usage: $0 <service>"
  echo "Example: $0 character"
  exit 1
fi

case "$SERVICE" in
  user|character|item|mission|event-log|ai|notification|gateway)
    ;;
  *)
    echo "ERROR: unsupported service: $SERVICE"
    exit 1
    ;;
esac

AWS_REGION="${AWS_REGION:-ap-northeast-2}"
AWS_PROFILE="${AWS_PROFILE:-p5laris-terraform}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-416170614910}"

IMAGE_REPO="p5laris-${SERVICE}"
IMAGE_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${IMAGE_REPO}"
TAG="eks-arm64-${SERVICE}-$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M)"

echo "== service =="
echo "$SERVICE"

echo "== build bootJar =="
./gradlew ":${SERVICE}:clean" ":${SERVICE}:bootJar"

JAR_FILE="$(find "${SERVICE}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*plain*' | head -1)"

if [ ! -f "$JAR_FILE" ]; then
  echo "ERROR: bootJar not found: ${SERVICE}/build/libs"
  exit 1
fi

echo "== jar =="
echo "$JAR_FILE"

echo "== ecr login =="
aws ecr get-login-password \
  --profile "$AWS_PROFILE" \
  --region "$AWS_REGION" \
| docker login \
  --username AWS \
  --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "== buildx build/push linux/arm64 =="
docker buildx build \
  --platform linux/arm64 \
  --build-arg JAR_FILE="$JAR_FILE" \
  -t "${IMAGE_URI}:${TAG}" \
  --push \
  .

echo "== inspect image =="
docker buildx imagetools inspect "${IMAGE_URI}:${TAG}" | grep -E 'Name:|MediaType:|Platform:|linux/arm64|linux/amd64' || true

echo "== result =="
echo "SERVICE=$SERVICE"
echo "IMAGE=${IMAGE_URI}:${TAG}"
echo "TAG=$TAG"
