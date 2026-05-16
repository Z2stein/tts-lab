#!/usr/bin/env bash
set -euo pipefail

workflow_file=".github/workflows/deploy.yml"

rg -n -F --no-heading -- "--set audiobookWorkflow.googleCredentialsSecretName" "$workflow_file" >/dev/null
rg -n -F --no-heading -- "--set audiobookWorkflow.googleCredentialsChecksum" "$workflow_file" >/dev/null

if rg -n -F --no-heading -- "ttsWorkbench.googleCredentialsSecretName" "$workflow_file" >/dev/null || \
   rg -n -F --no-heading -- "ttsWorkbench.googleCredentialsChecksum" "$workflow_file" >/dev/null; then
  echo "Found stale ttsWorkbench Helm set keys in $workflow_file." >&2
  exit 1
fi
