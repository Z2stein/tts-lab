#!/usr/bin/env bash
set -euo pipefail

rendered_gemini=$(helm template test charts/tts-lab --set chat.provider=gemini)
rendered_mock=$(helm template test charts/tts-lab --set chat.provider=mock)

echo "$rendered_gemini" | rg -q "name: GEMINI_API_KEY"
echo "$rendered_gemini" | rg -q "secretKeyRef"
echo "$rendered_gemini" | rg -q "name: gemini-api"
echo "$rendered_gemini" | rg -q "name: SPRING_AI_GOOGLE_GENAI_CHAT_OPTIONS_MODEL"
echo "$rendered_gemini" | rg -q "name: SPRING_AI_MODEL_CHAT"
echo "$rendered_gemini" | rg -q "value: \"google-genai\""
echo "$rendered_gemini" | rg -q "value: \"gemini-2.5-flash\""

echo "$rendered_mock" | rg -q "name: CHATBOT_PROVIDER"
echo "$rendered_mock" | rg -q "value: \"mock\""
echo "$rendered_mock" | rg -q "name: GEMINI_API_KEY"
echo "$rendered_mock" | rg -q "name: SPRING_AI_MODEL_CHAT"

if echo "$rendered_gemini" | rg -q "api-key:"; then
  echo "Found inline api-key in rendered manifests; expected secret reference only." >&2
  exit 1
fi
