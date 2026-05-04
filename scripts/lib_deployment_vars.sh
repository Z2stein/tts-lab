#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../shared/deployment/lib_deployment_vars.sh
source "$SCRIPT_DIR/../shared/deployment/lib_deployment_vars.sh"
