#!/usr/bin/env bash
set -euo pipefail

rendered=$(helm template test charts/tts-lab)

echo "$rendered" | rg -q "name: GEMINI_API_KEY"
echo "$rendered" | rg -q "secretKeyRef"
echo "$rendered" | rg -q "name: gemini-api"
if echo "$rendered" | rg -q "api-key:"; then
  echo "Found inline api-key in rendered manifests; expected secret reference only." >&2
  exit 1
fi
