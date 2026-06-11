#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="$ROOT_DIR/infra/terraform/envs/dev"
NS="p5laris-lab"

AWS_PROFILE="${AWS_PROFILE:-p5laris-terraform}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "ERROR: required command not found: $1" >&2
    exit 1
  }
}

need_cmd terraform
need_cmd kubectl
need_cmd aws
need_cmd jq
need_cmd sed
need_cmd base64

echo "== 1. Read Terraform outputs =="

cd "$TF_DIR"

RDS_ENDPOINT="$(terraform output -raw rds_endpoint)"
REDIS_ENDPOINT="$(terraform output -raw redis_endpoint)"
MSK_BOOTSTRAP="$(terraform output -raw msk_bootstrap_brokers)"
SECRET_ARN="$(terraform output -raw rds_master_user_secret_arn)"

echo "RDS_ENDPOINT=$RDS_ENDPOINT"
echo "REDIS_ENDPOINT=$REDIS_ENDPOINT"
echo "MSK_BOOTSTRAP=$MSK_BOOTSTRAP"
echo "SECRET_ARN=$SECRET_ARN"

echo
echo "== 2. Update Kubernetes overlay files =="

cd "$ROOT_DIR"

sed -i "s|^\([[:space:]]*REDIS_HOST:[[:space:]]*\).*|\1\"$REDIS_ENDPOINT\"|" \
  k8s/overlays/eks/configmap.eks.patch.yaml

sed -i "s|^\([[:space:]]*KAFKA_BOOTSTRAP_SERVERS:[[:space:]]*\).*|\1\"$MSK_BOOTSTRAP\"|" \
  k8s/overlays/eks/configmap.eks.patch.yaml

for f in k8s/overlays/eks/db-url-patches/*.yaml; do
  sed -i "s|jdbc:postgresql://[^:/\" ]*:5432/|jdbc:postgresql://${RDS_ENDPOINT}:5432/|g" "$f"
done

echo "Updated configmap and DB URL patches."

echo
echo "== 3. Delete previous one-shot Jobs if any =="

kubectl -n "$NS" delete job p5laris-kafka-topics --ignore-not-found=true || true
kubectl -n "$NS" delete pod pg-admin --ignore-not-found=true || true

echo
echo "== 4. Apply Kubernetes overlay =="

kubectl apply -k k8s/overlays/eks

echo
echo "== 5. Patch DB password from AWS Secrets Manager =="
echo "Password will not be printed."

DB_PASSWORD="$(
  AWS_PROFILE="$AWS_PROFILE" aws secretsmanager get-secret-value \
    --region "$AWS_REGION" \
    --secret-id "$SECRET_ARN" \
    --query SecretString \
    --output text \
  | jq -r '.password'
)"

if [ -z "$DB_PASSWORD" ] || [ "$DB_PASSWORD" = "null" ]; then
  echo "ERROR: failed to load DB password from Secrets Manager" >&2
  exit 1
fi

DB_PASSWORD_B64="$(printf '%s' "$DB_PASSWORD" | base64 | tr -d '\n')"

kubectl -n "$NS" patch secret p5laris-secret \
  --type merge \
  -p "{\"data\":{\"DB_PASSWORD\":\"$DB_PASSWORD_B64\"}}"

unset DB_PASSWORD
unset DB_PASSWORD_B64

echo "DB password patched into Kubernetes Secret."

echo
echo "== 6. Create service databases =="

cat <<YAML | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: pg-admin
  namespace: ${NS}
spec:
  restartPolicy: Never
  containers:
    - name: pg-admin
      image: pgvector/pgvector:pg16
      command: ["sleep", "3600"]
      env:
        - name: PGHOST
          value: "${RDS_ENDPOINT}"
        - name: PGPORT
          value: "5432"
        - name: PGUSER
          value: "p5laris_admin"
        - name: PGPASSWORD
          valueFrom:
            secretKeyRef:
              name: p5laris-secret
              key: DB_PASSWORD
YAML

kubectl -n "$NS" wait --for=condition=Ready pod/pg-admin --timeout=180s

for db in users character item mission event_log ai notification; do
  echo "ensure database: $db"
  kubectl -n "$NS" exec pg-admin -- sh -lc \
    "psql -d postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='${db}'\" | grep -q 1 || createdb '${db}'"
done

echo "Database list:"
kubectl -n "$NS" exec pg-admin -- sh -lc \
  "psql -d postgres -tAc \"SELECT datname FROM pg_database WHERE datname IN ('users','character','item','mission','event_log','ai','notification') ORDER BY datname;\""

echo
echo "== 7. Run Kafka topic Job =="

kubectl -n "$NS" delete job p5laris-kafka-topics --ignore-not-found=true
kubectl apply -f k8s/overlays/eks/kafka-topics-job.yaml

kubectl -n "$NS" wait \
  --for=condition=complete \
  job/p5laris-kafka-topics \
  --timeout=180s

kubectl -n "$NS" logs job/p5laris-kafka-topics

echo
echo "== 8. Scale application deployments sequentially =="

for svc in user character item mission event-log ai notification; do
  kubectl -n "$NS" scale "deploy/p5laris-${svc}" --replicas=0
done

kubectl -n "$NS" scale deploy/p5laris-gateway --replicas=2

sleep 20

for svc in user character item mission event-log ai notification; do
  echo
  echo "scale up: $svc"
  kubectl -n "$NS" scale "deploy/p5laris-${svc}" --replicas=1
  kubectl -n "$NS" rollout status "deploy/p5laris-${svc}" --timeout=420s
  sleep 20
done

kubectl -n "$NS" rollout status deploy/p5laris-gateway --timeout=300s

echo
echo "== 9. Current Kubernetes status =="

kubectl -n "$NS" get deploy,po,svc,ingress -o wide

echo
echo "OK: runtime sync completed."
