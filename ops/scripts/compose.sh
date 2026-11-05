#!/usr/bin/env bash
set -euo pipefail
ops_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
ops_env=${OPS_ENV_FILE:-"$ops_root/ops/.env.production"}
if [[ ! -f "$ops_env" ]]; then
  echo '운영 환경 파일이 없습니다. ops/.env.production.example을 복사해 설정하세요.' >&2
  exit 1
fi
exec docker compose --env-file "$ops_env" -f "$ops_root/ops/compose.production.yml" "$@"
