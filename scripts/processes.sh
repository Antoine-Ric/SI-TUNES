#!/usr/bin/env bash
# Shared lifecycle helpers, including support for macOS Bash 3.2.
children=()
cleanup() {
  local child
  for child in "${children[@]}"; do kill "$child" 2>/dev/null || true; done
  for child in "${children[@]}"; do wait "$child" 2>/dev/null || true; done
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
wait_for_children() {
  local child status
  while true; do
    for child in "${children[@]}"; do
      if ! kill -0 "$child" 2>/dev/null; then
        status=0
        wait "$child" || status=$?
        return "$status"
      fi
    done
    sleep 1
  done
}
