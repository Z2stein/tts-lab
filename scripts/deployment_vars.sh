#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-deploy}"
BRANCH_NAME="${2:-}"
SERVER_IP="${3:-178.105.41.67}"
GIT_SHA="${4:-}"

if [ -z "$BRANCH_NAME" ]; then
  echo "error: branch name is required" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib_deployment_vars.sh
source "$SCRIPT_DIR/lib_deployment_vars.sh"

TARGET_OUTPUT="$(resolve_target "$BRANCH_NAME" "$SERVER_IP")"
echo "$TARGET_OUTPUT"

if [ "$MODE" = "cleanup" ]; then
  DEPLOY_TYPE=$(echo "$TARGET_OUTPUT" | sed -n 's/^deploy_type=//p')
  cleanup_allowed "$DEPLOY_TYPE"
  exit 0
fi

if [ "$MODE" = "deploy" ]; then
  BRANCH_SLUG=$(echo "$TARGET_OUTPUT" | sed -n 's/^branch_slug=//p')
  build_image_tags "$BRANCH_SLUG" "$GIT_SHA"
  exit 0
fi

echo "error: unsupported mode '$MODE' (allowed: deploy|cleanup)" >&2
exit 1
