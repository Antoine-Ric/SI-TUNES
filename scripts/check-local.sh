#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
source scripts/local-env.sh
(cd backend && ./mvnw -B -ntp "-Dmaven.repo.local=$repo_root/.cache/m2" verify)
(cd frontend && npm run lint && npm run test && npm run build && npm run test:e2e)
