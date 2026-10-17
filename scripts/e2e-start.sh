#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
source scripts/local-env.sh
if [[ ! -f backend/target/si-tunes-api-0.1.0-SNAPSHOT.jar ]]; then
  echo 'Build the backend first: cd backend && ./mvnw verify' >&2
  exit 1
fi
source scripts/processes.sh
FIXTURE_PORT=9011 python3 scripts/mock-music-server.py &
children+=("$!")
python3 scripts/wait-for-http.py http://127.0.0.1:9011/__test/stats
LASTFM_API_KEY=fixture-key-not-real MUSICBRAINZ_BASE_URL=http://127.0.0.1:9011/ws/2 \
LASTFM_BASE_URL=http://127.0.0.1:9011/2.0/ PORT=8081 \
java -jar backend/target/si-tunes-api-0.1.0-SNAPSHOT.jar &
children+=("$!")
python3 scripts/wait-for-http.py http://127.0.0.1:8081/actuator/health
cd frontend
API_PROXY_TARGET=http://127.0.0.1:8081 ./node_modules/.bin/vite --host 127.0.0.1 --port 4174 &
children+=("$!")
wait_for_children
