#!/usr/bin/env bash
set -euo pipefail

DIST_DIR="frontend/dist/tts-lab-frontend/browser"
INDEX_FILE="$DIST_DIR/index.html"

if [ ! -f "$INDEX_FILE" ]; then
  echo "Missing built frontend index: $INDEX_FILE"
  exit 1
fi

if ! grep -q 'src="polyfills.js"' "$INDEX_FILE"; then
  echo "Missing polyfills.js script reference in built index.html"
  exit 1
fi

if [ ! -f "$DIST_DIR/polyfills.js" ]; then
  echo "Missing built polyfills.js bundle"
  exit 1
fi

echo "Frontend smoke check passed."
