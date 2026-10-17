#!/usr/bin/env bash
# Source this file only in Codex cloud tasks. Keep caches and generated config in /workspace.
export JAVA_HOME=/workspace/.tools/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_USER_HOME=/workspace/.cache/maven
export npm_config_cache=/workspace/.cache/npm
if [[ -f /workspace/.cache/si-tunes/truststore && "${JAVA_TOOL_OPTIONS:-}" != *"-Djavax.net.ssl.trustStore=/workspace/.cache/si-tunes/truststore"* ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/workspace/.cache/si-tunes/truststore"
fi
export PLAYWRIGHT_CHROMIUM_EXECUTABLE=/usr/bin/chromium
