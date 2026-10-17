#!/usr/bin/env bash
# Source from Bash or Zsh before running tools manually. No system installation changes.
if [[ -n "${BASH_VERSION:-}" ]]; then
  si_tunes_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
else
  si_tunes_root=$(cd "$(dirname "${(%):-%x}")/.." && pwd)
fi
if [[ -x "$si_tunes_root/.local/tools/node/bin/node" ]]; then
  export PATH="$si_tunes_root/.local/tools/node/bin:$PATH"
fi
if [[ -x "$si_tunes_root/.local/tools/jdk-21/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="$si_tunes_root/.local/tools/jdk-21/Contents/Home"
elif [[ -x "$si_tunes_root/.local/tools/jdk-21/bin/java" ]]; then
  export JAVA_HOME="$si_tunes_root/.local/tools/jdk-21"
elif [[ "$(uname -s)" == Darwin ]] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
export MAVEN_USER_HOME="${MAVEN_USER_HOME:-$si_tunes_root/.cache/maven}"
export npm_config_cache="${npm_config_cache:-$si_tunes_root/.cache/npm}"
export PLAYWRIGHT_BROWSERS_PATH="${PLAYWRIGHT_BROWSERS_PATH:-$si_tunes_root/.cache/ms-playwright}"
unset si_tunes_root
