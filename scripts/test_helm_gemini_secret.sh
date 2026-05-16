#!/usr/bin/env bash
set -euo pipefail

rendered_gemini=$(helm template test charts/tts-lab \
  --set chat.provider=gemini \
  --set audiobookWorkflow.googleCredentialsChecksum=gemini-secret-checksum)
rendered_gemini_with_tts=$(helm template test charts/tts-lab \
  --set chat.provider=gemini \
  --set audiobookWorkflow.googleCredentialsSecretName=google-tts-service-account \
  --set audiobookWorkflow.googleCredentialsChecksum=gemini-secret-checksum)
rendered_mock=$(helm template test charts/tts-lab --set chat.provider=mock)
rendered_switch_mock_to_gemini_before=$(helm template same-release charts/tts-lab --namespace same-namespace --set chat.provider=mock)
rendered_switch_mock_to_gemini_after=$(helm template same-release charts/tts-lab --namespace same-namespace \
  --set chat.provider=gemini \
  --set audiobookWorkflow.googleCredentialsSecretName=google-tts-service-account \
  --set audiobookWorkflow.googleCredentialsChecksum=after-mock-secret-checksum)
rendered_switch_gemini_to_mock_before=$(helm template same-release charts/tts-lab --namespace same-namespace \
  --set chat.provider=gemini \
  --set audiobookWorkflow.googleCredentialsSecretName=google-tts-service-account \
  --set audiobookWorkflow.googleCredentialsChecksum=before-mock-secret-checksum)
rendered_switch_gemini_to_mock_after=$(helm template same-release charts/tts-lab --namespace same-namespace --set chat.provider=mock)

# Gemini mode requires only Gemini chat credentials by default.
echo "$rendered_gemini" | rg -q "name: GEMINI_API_KEY"
echo "$rendered_gemini" | rg -q "secretKeyRef"
echo "$rendered_gemini" | rg -q "name: gemini-api"
echo "$rendered_gemini" | rg -q "name: SPRING_AI_GOOGLE_GENAI_CHAT_OPTIONS_MODEL"
echo "$rendered_gemini" | rg -q "name: SPRING_AI_MODEL_CHAT"
echo "$rendered_gemini" | rg -q "value: \"google-genai\""
echo "$rendered_gemini" | rg -q "value: \"gemini-2.5-flash\""
echo "$rendered_gemini" | rg -q "tts-lab/chat-provider: \"gemini\""
echo "$rendered_gemini" | rg -q "tts-lab/google-tts-credentials-checksum: \"gemini-secret-checksum\""
if echo "$rendered_gemini" | rg -q "name: TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64|key: service-account-json-b64"; then
  echo "Gemini chat rendered Google TTS credential env wiring without an explicit TTS secret." >&2
  exit 1
fi

# Google TTS credentials are injected only when a credentials secret is configured.
echo "$rendered_gemini_with_tts" | rg -q "name: TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64"
echo "$rendered_gemini_with_tts" | rg -q "name: google-tts-service-account"
echo "$rendered_gemini_with_tts" | rg -q "key: service-account-json-b64"

# Mock mode does not require or inject Google TTS credentials.
echo "$rendered_mock" | rg -q "name: CHATBOT_PROVIDER"
echo "$rendered_mock" | rg -q "value: \"mock\""
echo "$rendered_mock" | rg -q "name: GEMINI_API_KEY"
echo "$rendered_mock" | rg -q "name: SPRING_AI_MODEL_CHAT"
echo "$rendered_mock" | rg -q "tts-lab/chat-provider: \"mock\""
if echo "$rendered_mock" | rg -q "name: TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64|key: service-account-json-b64"; then
  echo "Mock mode rendered Google TTS credential env wiring; expected no TTS credential requirement." >&2
  exit 1
fi

# Simulated same namespace/release switch from mock to gemini reconciles the desired env state.
if echo "$rendered_switch_mock_to_gemini_before" | rg -q "name: TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64|key: service-account-json-b64"; then
  echo "Mock render before mock->gemini switch unexpectedly requires Google TTS credentials." >&2
  exit 1
fi
echo "$rendered_switch_mock_to_gemini_after" | rg -q "value: \"gemini\""
echo "$rendered_switch_mock_to_gemini_after" | rg -q "name: TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64"
echo "$rendered_switch_mock_to_gemini_after" | rg -q "tts-lab/google-tts-credentials-checksum: \"after-mock-secret-checksum\""

# Simulated same namespace/release switch from gemini to mock removes the env requirement.
echo "$rendered_switch_gemini_to_mock_before" | rg -q "name: TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64"
echo "$rendered_switch_gemini_to_mock_after" | rg -q "value: \"mock\""
if echo "$rendered_switch_gemini_to_mock_after" | rg -q "name: TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64|key: service-account-json-b64"; then
  echo "Mock render after gemini->mock switch still requires Google TTS credentials." >&2
  exit 1
fi

if echo "$rendered_gemini" | rg -q "api-key:"; then
  echo "Found inline api-key in rendered manifests; expected secret reference only." >&2
  exit 1
fi

if echo "$rendered_gemini_with_tts" | rg -q "service-account-json-b64:"; then
  echo "Found inline service account JSON in rendered manifests; expected secret reference only." >&2
  exit 1
fi

if echo "$rendered_gemini_with_tts" | rg -q "volumeMounts|/var/secrets/google|service-account\.json"; then
  echo "Found old file-based Google TTS credentials mount; expected environment secret reference only." >&2
  exit 1
fi

