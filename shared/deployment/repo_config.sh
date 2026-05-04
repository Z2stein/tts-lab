#!/usr/bin/env bash
set -euo pipefail

normalize_slug_chars() {
  local value="$1"
  echo "$value" \
    | tr '[:upper:]' '[:lower:]' \
    | sed -E 's/[^a-z0-9]+/-/g; s/-+/-/g; s/^-+//; s/-+$//'
}

resolve_app_slug() {
  local configured_app_slug="${1:-}"
  local repo_name="${2:-}"

  local app_slug="${configured_app_slug:-$repo_name}"
  app_slug="$(normalize_slug_chars "$app_slug")"

  if [ -z "$app_slug" ]; then
    app_slug="tts-lab"
  fi

  echo "$app_slug"
}

emit_repo_config() {
  local configured_app_slug="${1:-}"
  local repo_name="${2:-}"

  local app_slug
  app_slug="$(resolve_app_slug "$configured_app_slug" "$repo_name")"

  echo "app_slug=$app_slug"
  echo "backend_image_name=${app_slug}-backend"
  echo "frontend_image_name=${app_slug}-frontend"
}

emit_repo_config "$@"
