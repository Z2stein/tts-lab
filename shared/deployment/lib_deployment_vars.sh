#!/usr/bin/env bash
set -euo pipefail

strip_prefix() {
  local branch="$1"
  if [[ "$branch" == */* ]]; then
    local branch_type="${branch%%/*}"
    local branch_rest="${branch#*/}"
    local branch_short_type="${branch_type:0:1}"
    branch="${branch_short_type}-${branch_rest}"
  fi
  echo "$branch"
}

normalize_slug_chars() {
  local value="$1"
  echo "$value" \
    | tr '[:upper:]' '[:lower:]' \
    | sed -E 's/[^a-z0-9]+/-/g; s/-+/-/g; s/^-+//; s/-+$//'
}

truncate_slug() {
  local value="$1"
  value="${value:0:14}"
  echo "$value" | sed -E 's/-+$//'
}

slugify_branch() {
  local branch="$1"
  local stripped normalized truncated

  stripped=$(strip_prefix "$branch")
  normalized=$(normalize_slug_chars "$stripped")
  truncated=$(truncate_slug "$normalized")

  if [ -z "$truncated" ]; then
    echo "preview"
  else
    echo "$truncated"
  fi
}

resolve_target() {
  local branch="$1"
  local server_ip="$2"
  local app_slug="${APP_SLUG:-tts-lab}"

  app_slug="$(normalize_slug_chars "$app_slug")"
  if [ -z "$app_slug" ]; then
    app_slug="tts-lab"
  fi

  if [ "$branch" = "main" ]; then
    echo "deploy_type=main"
    echo "branch_slug=main"
    echo "namespace=$app_slug"
    echo "release_name=$app_slug"
    echo "host=${app_slug}.${server_ip}.sslip.io"
    return
  fi

  if [ "$branch" = "develop" ]; then
    echo "deploy_type=develop"
    echo "branch_slug=dev"
    echo "namespace=${app_slug}-dev"
    echo "release_name=${app_slug}-dev"
    echo "host=dev.${app_slug}.${server_ip}.sslip.io"
    return
  fi

  local slug
  slug=$(slugify_branch "$branch")

  echo "deploy_type=feature"
  echo "branch_slug=$slug"
  echo "namespace=${app_slug}-$slug"
  echo "release_name=${app_slug}-$slug"
  echo "host=${slug}.${app_slug}.${server_ip}.sslip.io"
}

build_image_tags() {
  local branch_slug="$1"
  local git_sha="$2"

  if [ -z "$git_sha" ]; then
    echo "error: git sha is required" >&2
    return 1
  fi

  local short_sha="${git_sha:0:7}"
  echo "image_tag=${branch_slug}-${short_sha}"
  echo "branch_alias_tag=${branch_slug}-latest"
}

cleanup_allowed() {
  local deploy_type="$1"
  if [ "$deploy_type" = "main" ] || [ "$deploy_type" = "develop" ]; then
    echo "skip_cleanup=true"
  else
    echo "skip_cleanup=false"
  fi
}
