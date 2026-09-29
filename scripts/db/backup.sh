#!/usr/bin/env bash
set -euo pipefail

: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

DB_NAME="${DB_NAME:-nexo_estoque}"
DB_HOST="${DB_HOST:-}"
DB_PORT="${DB_PORT:-3306}"
OUT_DIR="${OUT_DIR:-./backups}"

if [[ -z "$DB_HOST" && -n "${DB_URL:-}" ]]; then
  JDBC="${DB_URL#jdbc:mysql://}"
  DB_HOST="${JDBC%%:*}"
  REST="${JDBC#*:}"
  if [[ "$REST" == "$JDBC" ]]; then
    DB_HOST="${JDBC%%/*}"
  else
    DB_PORT="${REST%%/*}"
  fi
  PATH_PART="${JDBC#*/}"
  DB_NAME="${PATH_PART%%\?*}"
fi

: "${DB_HOST:?DB_HOST or DB_URL is required}"

mkdir -p "$OUT_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="$OUT_DIR/nexo_estoque_$STAMP.sql.gz"

export MYSQL_PWD="$DB_PASSWORD"

mysqldump   --host="$DB_HOST"   --port="$DB_PORT"   --user="$DB_USER"   --single-transaction   --routines   --triggers   --events   --hex-blob   --skip-comments   "$DB_NAME" | gzip -9 > "$FILE"

gzip -t "$FILE"
sha256sum "$FILE" > "$FILE.sha256"

echo "$FILE"
