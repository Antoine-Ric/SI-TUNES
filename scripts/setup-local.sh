#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
for tool in curl python3 tar; do
  command -v "$tool" >/dev/null || { echo "Install $tool before running setup." >&2; exit 1; }
done
source scripts/local-env.sh
case "$(uname -s)" in
  Darwin) node_os=darwin; java_os=macos ;;
  Linux) node_os=linux; java_os=linux ;;
  *) echo 'Local setup supports macOS and Linux.' >&2; exit 1 ;;
esac
case "$(uname -m)" in
  arm64|aarch64) node_arch=arm64; java_arch=aarch64 ;;
  x86_64) node_arch=x64; java_arch=x64 ;;
  *) echo 'Unsupported CPU architecture.' >&2; exit 1 ;;
esac
mkdir -p .local/tools .cache
download_dir=$(mktemp -d "$repo_root/.cache/setup.XXXXXX")
trap 'rm -rf "$download_dir"' EXIT
download() {
  curl --fail --silent --show-error --location --retry 2 --connect-timeout 15 "$1" -o "$2"
}
verify_archive() {
  python3 - "$1" "$2" <<'PY'
import hashlib, pathlib, sys
archive = pathlib.Path(sys.argv[1])
expected = pathlib.Path(sys.argv[2]).read_text().split()[0]
if hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest() != expected:
    raise SystemExit('Download checksum mismatch; installation stopped')
PY
}
if ! command -v node >/dev/null || ! node -e 'const [major, minor] = process.versions.node.split(".").map(Number); process.exit(major === 24 && minor >= 15 ? 0 : 1)' 2>/dev/null; then
  echo 'Installing project-local Node.js 24 LTS (verified SHA-256)…'
  download https://nodejs.org/dist/latest-v24.x/SHASUMS256.txt "$download_dir/node-checksums"
  node_archive=$(python3 - "$download_dir/node-checksums" "$node_os-$node_arch" "$download_dir/node-sha256" <<'PY'
import pathlib, sys
rows = [line.split() for line in pathlib.Path(sys.argv[1]).read_text().splitlines()]
digest, filename = next(row for row in rows if row[1].endswith('-' + sys.argv[2] + '.tar.gz'))
pathlib.Path(sys.argv[3]).write_text(digest)
print(filename)
PY
)
  download "https://nodejs.org/dist/latest-v24.x/$node_archive" "$download_dir/node.tar.gz"
  verify_archive "$download_dir/node.tar.gz" "$download_dir/node-sha256"
  mkdir -p .local/tools/node
  tar -xzf "$download_dir/node.tar.gz" --strip-components=1 -C .local/tools/node
  source scripts/local-env.sh
fi
java_version=$(java -version 2>&1 || true)
if [[ "$java_version" != *'version "21.'* ]]; then
  echo 'Installing project-local JDK 21 (verified SHA-256)…'
  java_url="https://download.oracle.com/java/21/latest/jdk-21_${java_os}-${java_arch}_bin.tar.gz"
  download "$java_url" "$download_dir/jdk.tar.gz"
  download "$java_url.sha256" "$download_dir/jdk-sha256"
  verify_archive "$download_dir/jdk.tar.gz" "$download_dir/jdk-sha256"
  mkdir -p .local/tools/jdk-21
  tar -xzf "$download_dir/jdk.tar.gz" --strip-components=1 -C .local/tools/jdk-21
  # Oracle's macOS archive nests the JDK bundle inside a platform directory.
  python3 - <<'PY'
from pathlib import Path
root = Path('.local/tools/jdk-21')
bundles = list(root.glob('*.jdk/Contents'))
if bundles and not (root / 'Contents').exists():
    bundles[0].rename(root / 'Contents')
PY
  source scripts/local-env.sh
fi
node --version
java -version
(cd backend && ./mvnw -B -ntp "-Dmaven.repo.local=$repo_root/.cache/m2" verify)
(cd frontend && npm ci && npm run build && npx playwright install chromium)
if [[ ! -e .env ]]; then
  cp .env.example .env
  chmod 600 .env
fi
echo 'Setup complete. Try the app: bash scripts/dev.sh --demo'
echo 'Run all checks: bash scripts/check-local.sh'
echo 'For live music discovery, set LASTFM_API_KEY in .env and run: bash scripts/dev.sh'
