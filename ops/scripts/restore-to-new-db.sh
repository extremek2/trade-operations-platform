#!/usr/bin/env bash
set -euo pipefail
ops_scripts=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
archive=${1:?Usage: restore-to-new-db.sh ARCHIVE NEW_DATABASE}
target=${2:?Usage: restore-to-new-db.sh ARCHIVE NEW_DATABASE}
[[ "$target" =~ ^[a-z][a-z0-9_]{0,62}$ ]] || { echo '새 DB 이름은 소문자·숫자·밑줄만 사용할 수 있습니다.' >&2; exit 1; }
[[ -f "$archive" ]] || { echo '백업 파일이 없습니다.' >&2; exit 1; }
# createdb rejects an existing database. This script never drops or overwrites any database.
"$ops_scripts/compose.sh" exec -T postgres pg_restore --list < "$archive" > /dev/null
"$ops_scripts/compose.sh" exec -T postgres sh -c 'exec createdb --username="$POSTGRES_USER" --template=template0 "$1"' sh "$target"
"$ops_scripts/compose.sh" exec -T postgres sh -c 'exec pg_restore --username="$POSTGRES_USER" --dbname="$1" --no-owner --no-privileges --exit-on-error' sh "$target" < "$archive"
printf 'Restored isolated database: %s. Keep application and mail disconnected pending schema checks and permission reconciliation.\n' "$target"
