#!/usr/bin/env bash
# Daily database dump on the server, run by cron. Keeps the last KEEP_DAYS days.
# Restore: gunzip -c backups/<datei>.sql.gz | docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
set -euo pipefail

cd "$(dirname "$0")"
BACKUP_DIR=${BACKUP_DIR:-$PWD/backups}
KEEP_DAYS=${KEEP_DAYS:-14}

mkdir -p "$BACKUP_DIR"
file="$BACKUP_DIR/expenses-$(date +%F-%H%M).sql.gz"

docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' | gzip > "$file.tmp"
mv "$file.tmp" "$file"
find "$BACKUP_DIR" -name 'expenses-*.sql.gz' -mtime "+$KEEP_DAYS" -delete
echo "Backup geschrieben: $file"
