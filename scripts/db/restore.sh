#!/usr/bin/env bash
set -euo pipefail

: "${1:?Usage: restore.sh <backup.sql.gz>}"
: "${DB_HOST:?DB_HOST is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${CONFIRM_RESTORE:?Set CONFIRM_RESTORE=YES to allow restore}"

if [[ "$CONFIRM_RESTORE" != "YES" ]]; then
  echo "Restore cancelled: CONFIRM_RESTORE must equal YES." >&2
  exit 2
fi

FILE="$1"
if [[ ! -f "$FILE" ]]; then
  echo "Backup file not found: $FILE" >&2
  exit 3
fi

if [[ -f "$FILE.sha256" ]]; then
  sha256sum -c "$FILE.sha256"
fi

gzip -t "$FILE"

export MYSQL_PWD="$DB_PASSWORD"
gunzip -c "$FILE" | mysql   --host="$DB_HOST"   --port="${DB_PORT:-3306}"   --user="$DB_USER"   "$DB_NAME"

echo "Restore completed into $DB_NAME"
