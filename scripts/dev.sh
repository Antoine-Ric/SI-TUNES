#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
source scripts/local-env.sh
demo=false
case "${1:-}" in
  --demo) demo=true ;;
  '') ;;
  *) echo 'Usage: bash scripts/dev.sh [--demo]' >&2; exit 1 ;;
esac
if [[ -f .env && "$demo" == false ]]; then
  # .env is a trusted local shell configuration file, kept out of Git.
  lastfm_from_environment=${LASTFM_API_KEY:-}
  set -a
  source .env
  set +a
  export LASTFM_API_KEY=${lastfm_from_environment:-${LASTFM_API_KEY:-}}
  unset lastfm_from_environment
fi
if [[ ! -f backend/target/si-tunes-api-0.1.0-SNAPSHOT.jar || ! -d frontend/node_modules ]]; then
  echo 'Run setup first: bash scripts/setup-local.sh (see README).' >&2
  exit 1
fi
source scripts/processes.sh
if [[ "$demo" == true ]]; then
  echo 'DEMO MODE: fixed test songs, no live provider requests. Try After-hours drive.'
  FIXTURE_PORT=9012 python3 scripts/mock-music-server.py &
  children+=("$!")
  python3 scripts/wait-for-http.py http://127.0.0.1:9012/__test/stats
  LASTFM_API_KEY=fixture-key-not-real
  export MUSICBRAINZ_BASE_URL=http://127.0.0.1:9012/ws/2
  export LASTFM_BASE_URL=http://127.0.0.1:9012/2.0/
fi
if [[ -z "${LASTFM_API_KEY:-}" ]]; then
  echo 'LASTFM_API_KEY is missing. The UI and API will run, but real suggestions return a configuration error.' >&2
fi
LASTFM_API_KEY="${LASTFM_API_KEY:-}" PORT="${PORT:-8080}" java -jar backend/target/si-tunes-api-0.1.0-SNAPSHOT.jar &
children+=("$!")
python3 scripts/wait-for-http.py "http://127.0.0.1:${PORT:-8080}/actuator/health"
cd frontend
VITE_DEMO_MODE="$demo" API_PROXY_TARGET="http://127.0.0.1:${PORT:-8080}" ./node_modules/.bin/vite --host 127.0.0.1 --port 5173 &
children+=("$!")
echo 'Open http://127.0.0.1:5173 — Ctrl+C stops the app.'
wait_for_children
