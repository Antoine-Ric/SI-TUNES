#!/usr/bin/env bash
set -euo pipefail
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
mkdir -p /workspace/.tools /workspace/.cache/si-tunes /workspace/.cache/npm /workspace/.cache/maven /workspace/.cache/m2
if [[ ! -x /workspace/.tools/jdk-21/bin/javac ]]; then
  download_dir=$(mktemp -d /tmp/si-tunes-jdk.XXXXXX)
  trap 'rm -rf "$download_dir"' EXIT
  curl --fail --silent --show-error --retry 2 https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz -o "$download_dir/jdk.tar.gz"
  curl --fail --silent --show-error --retry 2 https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz.sha256 -o "$download_dir/jdk.sha256"
  python3 - "$download_dir" <<'PY'
from pathlib import Path
import hashlib,sys
root=Path(sys.argv[1])
expected=(root/'jdk.sha256').read_text().split()[0]
if hashlib.sha256((root/'jdk.tar.gz').read_bytes()).hexdigest() != expected:
    raise SystemExit('JDK checksum mismatch; installation stopped')
PY
  mkdir -p /workspace/.tools/jdk-21
  tar -xzf "$download_dir/jdk.tar.gz" --strip-components=1 -C /workspace/.tools/jdk-21
fi
source scripts/cloud-env.sh
# Reuse standard CA roots and add the platform's provided CA; never disable TLS checks.
if [[ -n "${CODEX_PROXY_CERT:-}" && -f "$CODEX_PROXY_CERT" ]]; then
  cp "$JAVA_HOME/lib/security/cacerts" /workspace/.cache/si-tunes/truststore
  "$JAVA_HOME/bin/keytool" -importcert -noprompt -alias si-tunes-cloud-proxy -file "$CODEX_PROXY_CERT" \
    -keystore /workspace/.cache/si-tunes/truststore -storepass changeit
fi
source scripts/cloud-env.sh
python3 - <<'PY'
import os,urllib.parse,xml.etree.ElementTree as ET
root=ET.Element('settings',xmlns='http://maven.apache.org/SETTINGS/1.2.0')
uri=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if uri.hostname:
    proxy=ET.SubElement(ET.SubElement(root,'proxies'),'proxy')
    for name,value in [('id','cloud-egress'),('active','true'),('protocol','http'),('host',uri.hostname),('port',str(uri.port or 80)),('nonProxyHosts','localhost|127.0.0.1')]:
        ET.SubElement(proxy,name).text=value
ET.ElementTree(root).write('/workspace/.cache/si-tunes/maven-settings.xml',encoding='unicode',xml_declaration=True)
PY
cd backend
./mvnw -B -ntp -s /workspace/.cache/si-tunes/maven-settings.xml -Dmaven.repo.local=/workspace/.cache/m2 verify
cd ../frontend
npm ci
npm run lint
npm run test
npm run build
npm run test:e2e
