#!/usr/bin/env bash
set -euo pipefail

: "${COMPOSE_PROJECT_NAME:?missing COMPOSE_PROJECT_NAME}"
: "${APP_HOST:?missing APP_HOST}"
: "${ROUTER_NAME:?missing ROUTER_NAME}"
: "${IMAGE_TAG:?missing IMAGE_TAG}"
: "${GHCR_OWNER:?missing GHCR_OWNER}"

cd /opt/tts-lab

docker compose -p tts-proxy -f compose.traefik.yaml up -d

GHCR_OWNER="$GHCR_OWNER" \
IMAGE_TAG="$IMAGE_TAG" \
APP_HOST="$APP_HOST" \
ROUTER_NAME="$ROUTER_NAME" \
docker compose -p "$COMPOSE_PROJECT_NAME" -f compose.app.yaml pull

GHCR_OWNER="$GHCR_OWNER" \
IMAGE_TAG="$IMAGE_TAG" \
APP_HOST="$APP_HOST" \
ROUTER_NAME="$ROUTER_NAME" \
docker compose -p "$COMPOSE_PROJECT_NAME" -f compose.app.yaml up -d --remove-orphans
