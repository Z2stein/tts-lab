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

  local app_slug
  app_slug="$(normalize_slug_chars "$configured_app_slug")"

  if [ -z "$app_slug" ]; then
    echo "error: APP_SLUG is required" >&2
    return 1
  fi

  echo "$app_slug"
}

emit_repo_config() {
  local configured_app_slug="${1:-}"

  local app_slug
  app_slug="$(resolve_app_slug "$configured_app_slug")"

  echo "app_slug=$app_slug"
  echo "backend_image_name=${app_slug}-backend"
  echo "frontend_image_name=${app_slug}-frontend"
}

emit_repo_config "$@"
