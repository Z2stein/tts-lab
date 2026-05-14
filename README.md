# tts-lab

Lernprojekt mit Angular-Frontend und Spring-Boot-Backend.

?? ??

## Inhaltsverzeichnis

- [Repo-Onboarding](#repo-onboarding-kurzer-config-block)
- [What’s new](#whats-new)
- [Deployment-Status](#deployment-status)
- [Runtime-Architektur](#runtime-architektur)
- [Ziel-Umgebungen](#ziel-umgebungen)
- [Branch-Slug-Regel](#branch-slug-regel)
- [CI/CD (GitHub Actions)](#cicd-github-actions)
- [Lokal entwickeln](#lokal-entwickeln)
- [Database and prompt history](#database-and-prompt-history)
- [Akzeptanzkriterien (Textlänge)](#akzeptanzkriterien-textlänge)
- [Health endpoints](#health-endpoints)
- [API error responses](#api-error-responses)
- [Authentication modes](#authentication-modes)
- [Audiobook Studio MVP](#audiobook-studio-mvp)
- [Chatbot (MVP)](#chatbot-mvp)
- [Request limits (MVP)](#request-limits-mvp)

## What’s new

This chat upgraded the existing Audiobook Studio MVP from an internal workflow page into a more premium, cinematic AI audiobook studio experience:

- `/audiobook-studio` now opens with a frontend-only hero section: “Give every character in your story a voice.”
- The first viewport shows the intended product promise visually: pasted story text flows into a detected cast card and an audio waveform preview.
- The hero includes `Create audio story` and `Listen to demo` actions; the primary CTA scrolls to and focuses the existing story textarea.
- A new “From plain text to performed story” section explains the four-step journey: paste story, discover cast, direct performance, generate audio.
- The existing functional workflow remains below the motivational sections and still uses the same Angular component state and backend APIs.
- Detected cast cards now feel more like creative character/voice cards, with initials, stronger hierarchy, voice badges, and subtle per-card accent glows.
- Technical production fields such as language code, model name, and audio encoding are tucked behind `Advanced production settings`, while the story direction stays visible.
- This feature adds backend workflow changes, a persisted project-title update endpoint, contract updates, and matching frontend behavior.
- Verification run for this chat: `npm run build`, `npm run test -- --watch=false --browsers=ChromeHeadless`, and `npm run test:e2e -- e2e/audiobook-studio.spec.ts`.

## Repo-Onboarding (kurzer Config-Block)

Für ein neues Repository muss nur ein kleiner Satz an Variablen gesetzt werden (statt Shell-Logik zu ändern):

```text
# GitHub Actions Repository Variables (Settings ? Secrets and variables ? Actions)
APP_SLUG=<kebab-case-app-name>      # required app/release/host slug
BASE_DOMAIN=<public-base-domain>    # required public wildcard DNS domain
HETZNER_PUBLIC_IP=<server-public-ip> # required deployment target (SSH/k3s server)
```

Naming-Konventionen:

- `APP_SLUG` in `kebab-case`.
- `BASE_DOMAIN` ist die öffentliche Wildcard-Domain für Ingress-Hosts.
- `HETZNER_PUBLIC_IP` bleibt nur der SSH/k3s-Zielserver und wird nicht mehr in öffentlichen Hostnamen verwendet.
- Aus `APP_SLUG` werden automatisch abgeleitet:
  - Namespaces/Releases: `<app-slug>`, `<app-slug>-dev`, `<app-slug>-<branch-slug>`
  - Hosts: `<app-slug>.<base-domain>`, `dev.<app-slug>.<base-domain>`, `<branch-slug>.<app-slug>.<base-domain>`
  - GHCR-Images: `<app-slug>-backend`, `<app-slug>-frontend`

Wiederverwendbare Deployment-Bausteine liegen unter `shared/deployment/`:

- `repo_config.sh`: erzeugt `app_slug`, `backend_image_name`, `frontend_image_name`
- `lib_deployment_vars.sh`: zentrale Namespace/Release/Host-Logik (wird von `scripts/lib_deployment_vars.sh` nur noch eingebunden)

## Deployment-Status

**Primärer Deployment-Weg ist k3s + Helm.**

- Keine Docker-Compose-Deployments mehr.
- Kein Docker-Traefik-Runtime-Setup mehr.
- Dockerfiles bleiben für den Image-Build erhalten.

## Runtime-Architektur

- Helm Chart: `charts/tts-lab`
- Ingress Controller: Traefik in k3s
- PostgreSQL runs in-cluster for Helm deployments and is wired to the backend through Kubernetes Secrets.
- Standard-Health-Probes im Helm-Chart:
  - Frontend: `GET /`
  - Backend: `GET /health`
- Routing:
  - `/` ? Frontend Service
  - `/api` ? Backend Service
  - `/oauth2` ? Backend Service
  - `/login/oauth2` ? Backend Service
  - `/logout` ? Backend Service
- Backend-Alias-Service `backend` bleibt standardmäßig aktiv für `http://backend:8080` im Frontend-Container.

### In-Repo TLS activation

TLS im Ingress kann direkt per Helm-Values aktiviert werden:

- `ingress.tls.enabled=true`
- `ingress.tls.secretName=<tls-secret-name>`
- `ingress.annotations.cert-manager.io/cluster-issuer=<issuer-name>` (z. B. `letsencrypt-prod`)

Der Ingress verwendet weiterhin `ingress.host` als Host für `rules` und TLS-Mapping.
Externe Voraussetzungen sind im Abschnitt **HTTPS-Voraussetzungen außerhalb des Repos (Status)** dokumentiert.

## Ziel-Umgebungen

- `main`
  - Namespace: `<app-slug>`
  - Release: `<app-slug>`
  - URL: `https://<app-slug>.<base-domain>`
- `develop`
  - Namespace: `<app-slug>-dev`
  - Release: `<app-slug>-dev`
  - URL: `https://dev.<app-slug>.<base-domain>`
- Feature-Branches
  - Namespace: `<app-slug>-<branch-slug>`
  - Release: `<app-slug>-<branch-slug>`
  - URL: `https://<branch-slug>.<app-slug>.<base-domain>`

## Branch-Slug-Regel

Für Feature-Branches im Workflow:

1. Prefix entfernen: `feature/`, `bugfix/`, `hotfix/`, `release/`
2. lowercase
3. Sonderzeichen ? `-`
4. Mehrfach-`-` reduzieren
5. führende/abschließende `-` entfernen
6. max. 10 Zeichen
7. falls abgeschnittenes Ende `-` ist: entfernen

Beispiel:

- `feature/codex-k3s-ganz-viel-mehr-text` ? `codex-k3s`

## CI/CD (GitHub Actions)

Workflow: `.github/workflows/deploy.yml`

The backend image build now runs the backend test suite, including OpenAPI contract validation against `shared/api-contract/tts-lab-openapi.yaml`, before producing the runtime jar.

Ablauf bei Push:

1. Branch-Typ erkennen (main/develop/feature)
2. Slug, Namespace, Release, Host berechnen
3. Frontend/Backend Image bauen
4. Images nach GHCR pushen
5. SSH auf Hetzner
6. Den effektiven Provider (`mock` oder `gemini`) einmal berechnen und durchgängig für Secret-Validierung, Secret-Reconciliation und Helm verwenden
7. Namespace idempotent anlegen/aktualisieren
8. `ghcr-pull-secret` idempotent im Namespace anlegen/aktualisieren
9. Wenn `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64` gesetzt ist, die Google-TTS-Credentials vor dem Helm-Upgrade als Kubernetes Secret anlegen/aktualisieren; der Chat-Provider `gemini` funktioniert auch ohne dieses optionale TTS-Secret
10. `helm upgrade --install --wait --timeout 5m` ausführen
11. Backend- und Frontend-Deployments per `kubectl rollout status` abwarten
12. Backend- und Frontend-Pods per `kubectl wait --for=condition=Ready pod -l ...` abwarten
13. Deployment-URL veröffentlichen; parallel zum Deployment-Pfad führt der separate Job `e2e-local` die mandatory Playwright-E2E-Tests lokal im GitHub-Actions-Runner mit Playwright-Webservern aus, inklusive realem Frontend-Backend-Check ohne Mock für die geprüfte Backend-Route

Die Pipeline schlägt fehl, wenn Rollout/Pod-Readiness nicht erreicht wird oder wenn der separate `e2e-local`-Job fehlschlägt. Feste Sleep-Zeiten sind nicht der primäre Synchronisationsmechanismus; die Pipeline nutzt Kubernetes-Readiness und die Helm-Chart-Probes (`GET /health` im Backend, `GET /` im Frontend).

Der lokale E2E-Job läuft mit `E2E_BASE_URL=http://127.0.0.1:4200` und `E2E_USE_LOCAL_SERVERS=true` (der Standard wäre ebenfalls lokal), startet also Backend und Frontend über die bestehende Playwright-`webServer`-Konfiguration. Vor den Playwright-Tests laufen dort zusätzlich die Frontend-Unit-Tests und der Frontend-Build. Er enthält weiterhin deterministische UI-Tests mit gemockten Backend-Routen und zusätzlich `real-backend-health.spec.ts`. Dieser reale Integrationscheck lädt das lokale Frontend und ruft aus dem Browser-Kontext `GET /api/health` auf. Die Route ist bewusst stabil, benötigt keine Anmeldung, keine CSRF-Token und keine externen Provider-Secrets. Der Test schlägt fehl, wenn der Browser das lokal gestartete Backend nicht erreicht, wenn die Antwort kein `200 {"status":"ok"}` ist, oder wenn das Frontend die Antwort nicht verarbeiten und anzeigen kann.

Cleanup:

- Bei Branch-Delete oder PR-Close werden Feature-Releases + Namespace entfernt.
- `main` und `develop` werden explizit nie gelöscht.

## Lokal entwickeln

### Backend

```bash
cd backend
gradle bootRun
```

### Frontend

```bash
cd frontend
npm install
npm start
```

### Lokale Checks und E2E

Empfohlene schnelle lokale/Codex-Checks sind Backend-Build/Unit-Tests, Frontend-Unit-Tests, Frontend-Linting und Frontend-Builds. E2E-Tests sind lokal optional und sollen gezielt laufen, wenn eine Änderung End-to-End-Verhalten, Routing, Auth, Deployment-Verhalten oder mehrere App-Schichten betrifft.

Contract testing ist Teil der regulären Validierung:

- Backend-Controller-Tests prüfen Responses gegen `shared/api-contract/tts-lab-openapi.yaml`.
- Frontend-Typen werden aus derselben OpenAPI-Datei generiert; `cd frontend && npm run verify:api-contract` prüft, dass die generierten Typen zur Spezifikation passen.
- Shared fixtures liegen unter `test-contracts/` und werden von Frontend- und Backend-Tests gemeinsam verwendet.
- Wenn ein Mock eine echte API-/AI-Request- oder Response-Struktur beschreibt, soll er aus `test-contracts/` geladen werden und nicht als hart codiertes Inline-Objekt dupliziert werden.

Die OpenAPI-Contract-Datei liegt unter `shared/api-contract/tts-lab-openapi.yaml`; die Backend-Tests validieren controller responses gegen genau diese Datei.
Die Frontend-Contract-Typen werden aus dieser OpenAPI-Datei generiert; `frontend/src/app/shared/api-contract.generated.ts` ist die generierte Quelle, `frontend/src/app/shared/api-contract.ts` ist nur ein dünner Alias-Layer, und `cd frontend && npm run verify:api-contract` prüft die Generierung gegen dieselbe Quelle.

```bash
cd backend
gradle build

cd ../frontend
npm run lint
npx eslint --no-ignore e2e/**/*.ts
CHROME_BIN="${CHROME_BIN:-/tmp/chrome-no-sandbox}" npm test
npm run build
```

Hinweis: `npm run lint` deckt nur `src/**/*.ts` ab. Playwright-Spezifikationen unter `frontend/e2e/` werden mit `npx eslint --no-ignore e2e/**/*.ts` separat geprüft.

Lokale E2E-Tests starten standardmäßig Backend und Frontend über Playwright:

```bash
cd frontend
npm run test:e2e
```

Die Playwright-Suite unterscheidet zwischen:

- gemockten UI-E2E-Tests (`audiobook-studio.spec.ts`), die gezielt Backend-Routen mocken, um UI-Erfolg und UI-Fehler deterministisch zu prüfen;
- realen Frontend-Backend-E2E-Tests (`real-backend-health.spec.ts`), die die geprüfte Backend-Route nicht mocken und standardmäßig über die lokal gestarteten Playwright-Webserver laufen.

E2E gegen eine deployte Umgebung:

```bash
cd frontend
E2E_BASE_URL="https://<deployed-host>" E2E_USE_LOCAL_SERVERS=false npm run test:e2e
```

Wichtig: Obwohl E2E lokal/Codex optional ist, ist E2E in der CI/CD-Pipeline mandatory und läuft dort lokal im GitHub-Actions-Runner mit `E2E_USE_LOCAL_SERVERS=true`.

Wichtig: Contract testing ist ebenfalls mandatory für API-Änderungen. Wenn sich Request-/Response-Shapes, Statuscodes, Header oder Beispielpayloads ändern, müssen die OpenAPI-Spezifikation, die Shared Fixtures und die betroffenen Backend-/Frontend-Tests gemeinsam angepasst werden.

## Database and prompt history

Prompt history is now persisted with Flyway-managed tables:

- `prompt_history` stores every submitted prompt with user id, optional email, model type, optional provider/model name, prompt text, request status, and timestamp.
- `prompt_usage` stores per-user, per-model request counters so the backend can grow into personal rate limits later.

Behavior by environment:

- Local backend runs use the repository's default H2 file database unless `SPRING_DATASOURCE_*` is set.
- Helm deployments use PostgreSQL in the namespace, with a StatefulSet and PVC.
- `main` and `develop` keep their PostgreSQL data across upgrades.
- Feature namespaces can be deleted cleanly, which removes their database with the namespace.

Prompt history is visible in the frontend `Prompt History` tab and is filtered to the current authenticated user. The backend also records prompts from the text chat flow and the TTS workbench flow.


## Health endpoints

- `GET /health` is the backend pod health endpoint used by Kubernetes probes.
- `GET /api/health` is protected like the rest of the API surface.

## API error responses

Backend API failures use a structured, frontend-safe JSON response:

```json
{
  "status": 502,
  "code": "AUDIOBOOK_WORKFLOW_PROVIDER_FAILED",
  "message": "The speaker voice analysis provider is currently unavailable. Please try again later.",
  "details": null,
  "requestId": "request-or-generated-id"
}
```

Fields:

- `status`: HTTP status code.
- `code`: stable application error code for clients/tests.
- `message`: safe user-facing message suitable for display in the frontend.
- `details`: optional safe details, mostly for validation hints; stack traces/secrets/internal implementation details are not exposed.
- `requestId`: incoming `X-Request-Id` when present, otherwise generated by the backend and also returned as response header.

The backend logs the full exception/cause for debugging while keeping frontend responses safe. Frontend clients prefer the backend `message` and fall back to a generic HTTP-status message only when no structured backend error is available.

## Authentication modes

The backend supports environment-driven authentication with `AUTH_MODE`:

- `AUTH_MODE=google` for stable environments (`main` and `develop` as `dev`).
- `AUTH_MODE=mock` for feature branches and local testing.

Required variables:

- `AUTH_MODE` (`google|mock`)
- `ENVIRONMENT` (`main|dev|feature|prod`)
- `APP_BASE_URL` (external HTTPS URL)

Google mode additionally requires:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

Mock mode uses:

- `MOCK_USER_ID`
- `MOCK_USER_EMAIL`
- `MOCK_USER_NAME`
- `MOCK_USER_ROLES` (comma-separated)

Safety guardrails:

- `AUTH_MODE=mock` fails startup when `ENVIRONMENT=main` or `ENVIRONMENT=prod`.
- `AUTH_MODE=google` fails startup when Google credentials are missing.

Google redirect URI must match Spring callback path exactly:

- `https://<host>/login/oauth2/code/google`

The workflow `.github/workflows/deploy.yml` now sets auth by branch type:

- `main` -> Google auth (`ENVIRONMENT=main`)
- `develop` -> Google auth (`ENVIRONMENT=dev`)
- all other branches -> mock auth (`ENVIRONMENT=feature`)

Feature deployments do not create or inject Google OAuth secrets.

Frontend behavior note:

- On startup, the frontend first checks `/api/me` and shows a short loading state until auth is resolved. If `/api/me` fails (for example due to CORS/network issues), the UI no longer hangs in loading and falls back to unauthenticated with a visible error message and browser console logs.
- The authenticated app uses a shared header and client-side routes: `/` for the landing page and `/audiobook-studio` for the Audiobook Studio MVP. Unknown frontend routes redirect to `/`.
- Only authenticated users see the routed app pages and chatbot widget.
- Unauthenticated users see only the sign-in UI, which starts OAuth via `/oauth2/authorization/google`.
- Logged-in users also see their auth state in the header and a logout button that calls `/logout` and returns to `/`.


## Audiobook Studio MVP

Audiobook Studio is a user-friendly frontend flow built on top of the existing audiobook workflow API. It is available at `/audiobook-studio` and reframes the same pipeline as story input, cast discovery, script preview, performance notes, an audio production plan, and generated audio.

The MVP now also persists and edits the project title. It reuses the existing speaker analysis, speaker split, emotion annotation, final request preview, single-speaker render plan, and audio creation APIs while presenting story-focused language and a dark cinematic studio interface.

The first workflow step asks the model for both speakers and a project title. The title is persisted on the audiobook project, displayed in the studio and library views, and can be edited through `PATCH /api/audiobooks/{id}`.

The page now starts with a product-led landing/workflow layer:

- A premium hero with the headline “Give every character in your story a voice.”
- A static visual demo that shows story text transforming into a detected cast and an audio waveform.
- Benefit chips for `Multi-speaker`, `Speech segment detection`, `Voice previews`, and `Export MP3`.
- A four-card “From plain text to performed story” journey section.
- Hero CTAs that keep the existing workflow reachable: `Create audio story` focuses the story input, and `Listen to demo` loads the sample story before focusing the textarea.

The page now includes a frontend-only review and correction layer before generation:

- Cast cards support `Edit`, `Save`, and `Cancel` for `speakerName`, `roleDescription`, and `voiceSuggestion`.
- Speaker names are formatted for display while preserving their original backend/internal value unless saved by the user.
- Script turns support one-at-a-time editing for `speaker` and `text`.
- Script approval is required before performance notes can be generated.
- Editing the script after performance notes exist marks those notes stale and blocks audio production planning until notes are regenerated.
- These review states are local component state only; no persistence, auth, deployment, database, provider, or Helm behavior changed.

Audiobook Studio calls the workflow endpoints under `/api/audiobooks/workflow/*`. The previous route names and page are gone.

- `POST /api/audiobooks/workflow/speaker-voice-analysis` with raw dialogue returns suggested rows containing `speakerName`, `roleDescription`, and `voiceSuggestion`.
- `POST /api/audiobooks/workflow/speaker-split-analysis` with raw dialogue, speaker suggestions, and `projectId` returns `turns` containing `speaker` and `text` while persisting split rows to `audiobook_speech_segment`.
- `POST /api/audiobooks/workflow/script-preview-save` with `projectId` and ordered script turns persists edited preview rows and returns the saved `turns`.
- `POST /api/audiobooks/workflow/emotion-annotation-analysis` with `projectId` returns annotated `turns` containing `speaker` and marked-up `text`. The server reloads the persisted `SCRIPT_PREVIEW` rows for that project, uses those turns as the annotation input, and persists the styled text to `audiobook_speech_segment.styled_text` on the matching preview rows.
- `POST /api/audiobooks/workflow/final-request-preview` with prompt, speakers, annotated turns, language code, model name, and audio encoding returns the final provider request JSON preview.
- `POST /api/audiobooks/workflow/single-speaker-render-plan` with the final request JSON returns `renderRequests`, where each item is provider-shaped JSON containing `input.text`, `voice.languageCode`, `voice.name`, `voice.modelName`, and `audioConfig.audioEncoding`.
- `POST /api/audiobooks/workflow/create-audio` with `renderRequests` returns a downloadable MP3 for one render request or one concatenated MP3 for multiple requests. It stores the generated preview parts on the project, but it does not mark the workflow as current by itself.
- `POST /api/audiobooks/workflow/projects/{projectId}/audio-generated` marks the generated preview as current after the merged audiobook preview is complete and returns the refreshed workflow snapshot.

Single-speaker render requests now include backend-only persistence metadata such as `input.segmentOrderIndex` and `voice.speakerName` so generated audio can be attached back to the saved script-preview rows. Provider request builders still ignore those extra fields, so the preview JSON remains compatible with the external TTS request shape, for example:

```json
{
  "input": {
    "text": "[calm]The rain had turned the windows silver by the time they reached the old station café.\n[serious]Mara folded the letter twice, then unfolded it again.",
    "segmentOrderIndex": 0
  },
  "voice": {
    "languageCode": "en-US",
    "speakerName": "Narrator",
    "name": "Schedar",
    "modelName": "{{google-model}}"
  },
  "audioConfig": {
    "audioEncoding": "MP3"
  }
}
```

Runtime behavior follows the existing chatbot provider mode where possible:

- `CHATBOT_PROVIDER=mock` returns deterministic local speaker suggestions, speaker splitting, emotion annotation, final JSON preview data, and mock MP3 bytes for audio creation. It starts without `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64`.
- `CHATBOT_PROVIDER=gemini` asks the configured chat provider for structured speaker/voice, speaker split, and emotion annotation output. Provider failures or invalid provider output return structured API errors so the frontend can show a clear failure instead of silently displaying fallback data.
- Google Cloud Text-to-Speech credentials are loaded by the backend from the optional backend-only `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64` environment variable, which contains the Base64-encoded service account JSON. When it is not configured, the backend still starts and Gemini chat works, but `create-audio` returns a structured TTS provider error instead of real Google Cloud audio. The secret is never exposed to Angular.

Prompts are accessed through a `AudiobookWorkflowPromptProvider` abstraction. The current implementation returns static defaults, but the service structure is intentionally open for future prompts loaded from configuration, a database, an admin UI, project settings, or tenant-specific settings.

Automated tests use mock behavior and do not call Gemini APIs.

## Chatbot (MVP)

The frontend now includes a reusable chatbot widget component that calls `POST /api/chat` on the backend. The frontend never calls Gemini directly.

### Helm/runtime configuration

- `chat.geminiModel` controls the Gemini model (`gemini-2.5-flash` by default).
- `chat.provider` controls backend runtime provider (`gemini` or `mock`); the chart default is `mock` so local/feature-style installs do not require `GEMINI_API_KEY`.
- `chat.realProviderOnFeatureBranches` defaults to `false` and is used by the deploy workflow to keep feature branches in mock chatbot mode by default.
- `audiobookWorkflow.googleCredentialsSecretName` controls the optional Kubernetes secret that provides `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64` to the backend. Leave it empty to run Gemini chat without Google Cloud TTS credentials.
- `audiobookWorkflow.googleCredentialsChecksum` is written to the backend pod template annotation so a Google TTS credential change creates a new backend ReplicaSet.
- The frontend remains provider-agnostic and always calls `POST /api/chat`.

### Required secret

- `GEMINI_API_KEY` is required for `main` and `develop` deployments (provider = `gemini`).
- `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64` is optional for deployments using `CHATBOT_PROVIDER=gemini`; set it only when `create-audio` should call Google Cloud Text-to-Speech.
- Feature branch deployments run with provider = `mock` by default, so `GEMINI_API_KEY` and `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64` are not required in that default mode.
- If feature branches explicitly enable the real provider (`CHAT_REAL_PROVIDER_ON_FEATURE_BRANCHES=true` in GitHub Actions variables), then `GEMINI_API_KEY` is required there as well. If `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64` is also set, redeploying creates/updates the optional TTS secret before Helm runs; redeploying without it removes the backend TTS env wiring without deleting the namespace.
- The key is injected via Kubernetes `secretKeyRef` only and is never exposed to Angular.

### Local development

Mock chatbot is the safe default for local runs:

```bash
cd backend
./gradlew bootRun
```

To test with real Gemini locally:

```bash
export CHATBOT_PROVIDER=gemini
export GEMINI_API_KEY=your-gemini-api-key
export TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64=base64-encoded-google-service-account-json
cd backend
./gradlew bootRun
```

Then run frontend normally:

```bash
cd frontend
npm install
npm start
```

Automated backend/frontend tests use mocks and do not call Gemini APIs.


## Request limits (MVP)

Every authenticated API request is counted against a per-user, per-model fixed window. The defaults are:

- `SPEECH_MODEL`: `600` words per `12h`
- `TEXT_MODEL`: `600` words per `12h`

The request unit is `WORDS` by default, but the backend can also measure `TOKENS` if that deployment setting changes.

How the limit works:

1. New users start with the deployment defaults from `request-limits.*` / `REQUEST_LIMITS_*`.
2. A per-user override in the database wins over the deployment default.
3. Usage is tracked separately for `TEXT_MODEL` and `SPEECH_MODEL`.
4. The remaining amount is shown in the top bar after login.
5. All `/api/*` endpoints require login.

Deployment config:

- `REQUEST_LIMITS_ENABLED` (default `true`)
- `REQUEST_LIMITS_WINDOW` (default `12h`)
- `REQUEST_LIMITS_SPEECH_MODEL_LIMIT` (default `600`)
- `REQUEST_LIMITS_TEXT_MODEL_MULTIPLIER` (default `1`)
- `REQUEST_LIMITS_UNIT` (default `WORDS`)

Database override table:

- `request_rate_limit_overrides`

If a request exceeds the remaining amount, the backend returns HTTP `429` with a structured error response and `Retry-After`.

The prompt history shows the model type that was actually used:

- `TEXT_MODEL` for chat and Gemini-based analysis requests
- `SPEECH_MODEL` for TTS audio creation

That keeps the history table, top-bar counters, and backend enforcement lined up.





