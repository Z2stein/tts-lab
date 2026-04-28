#!/usr/bin/env bash
set -euo pipefail

: "${COMPOSE_PROJECT_NAME:?missing COMPOSE_PROJECT_NAME}"

cd /opt/tts-lab

docker compose -p "$COMPOSE_PROJECT_NAME" -f compose.app.yaml down --remove-orphans || true
