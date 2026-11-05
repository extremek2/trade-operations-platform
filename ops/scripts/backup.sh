#!/usr/bin/env bash
set -euo pipefail
umask 077
ops_scripts=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
backup_dir=${1:?Usage: backup.sh OUTPUT_DIRECTORY}
mkdir -p -- "$backup_dir"
backup_path=$(mktemp "$backup_dir/trade-ops-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX.dump")
# A failed dump stays under its unique filename for investigation; it is never promoted as verified.
"$ops_scripts/compose.sh" exec -T postgres sh -c 'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom --no-owner --no-privileges' > "$backup_path"
"$ops_scripts/compose.sh" exec -T postgres pg_restore --list < "$backup_path" > /dev/null
sha256sum "$backup_path" > "$backup_path.sha256"
printf 'Archive created (catalog checked; restore rehearsal required): %s\n' "$backup_path"
