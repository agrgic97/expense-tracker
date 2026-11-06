#!/usr/bin/env bash
# Builds the images for the server's CPU, transfers them via SSH and restarts the stack.
# Usage: ./deploy/deploy.sh deploy@deinearmutkotztmichan.com
set -euo pipefail

SERVER=${1:?Aufruf: ./deploy/deploy.sh benutzer@server}
REMOTE_DIR=${REMOTE_DIR:-/opt/expense-tracker}
PLATFORM=${PLATFORM:-linux/amd64}

cd "$(dirname "$0")/.."

if [[ -n "$(git status --porcelain)" ]]; then
  echo "⚠️  Es gibt nicht committete Änderungen – sie werden mit deployt." >&2
fi

for app in backend frontend; do
  echo "▶ Baue $app für $PLATFORM …"
  docker buildx build --platform "$PLATFORM" -t "expense-tracker-$app:latest" --load "$app"
done

echo "▶ Übertrage Images …"
docker save expense-tracker-backend:latest expense-tracker-frontend:latest | gzip | ssh "$SERVER" 'gunzip | docker load'

echo "▶ Übertrage Konfiguration …"
scp deploy/compose.yaml deploy/Caddyfile deploy/backup.sh "$SERVER:$REMOTE_DIR/"

echo "▶ Starte Stack …"
ssh "$SERVER" "cd '$REMOTE_DIR' && chmod +x backup.sh && docker compose up -d --remove-orphans && docker image prune -f >/dev/null"

echo "▶ Warte auf das Backend …"
for _ in $(seq 1 30); do
  if ssh "$SERVER" "cd '$REMOTE_DIR' && docker compose ps backend --format '{{.Health}}'" | grep -q healthy; then
    echo "✅ Deployment erfolgreich"
    exit 0
  fi
  sleep 3
done
echo "❌ Backend ist nicht healthy geworden. Logs: ssh $SERVER 'cd $REMOTE_DIR && docker compose logs backend'" >&2
exit 1
