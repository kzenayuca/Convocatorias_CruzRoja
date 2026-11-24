#!/usr/bin/env bash
# Retry only wrapper/distribution startup, never the build or its tests.
# Run from backend; both backend and functional jobs share the wrapper cache.
set -euo pipefail

for attempt in 1 2 3 4; do
  if bash ./mvnw -B -ntp --version; then
    exit 0
  fi
  if [ "$attempt" -eq 4 ]; then
    echo "::error::Maven Wrapper no pudo iniciar tras 4 intentos." >&2
    exit 1
  fi
  delay=$((15 * (2 ** (attempt - 1))))
  echo "::warning::Maven Wrapper no disponible; reintento en ${delay}s."
  sleep "$delay"
done
