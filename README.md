# tts-lab

Lernprojekt mit Angular-Frontend und Spring-Boot-Backend.

⚠️ ⚠️

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
- [TTS Workbench (MVP)](#tts-workbench-mvp)
- [Chatbot (MVP)](#chatbot-mvp)
- [Request limits (MVP)](#request-limits-mvp)

## What’s new

### Audiobook Library v2 Data Model Migration

A comprehensive data model upgrade enables rich audiobook library displays with complete generation history tracking:

**New Schema (Flyway V6):**
- **Character** table: Cast management with voice assignments, role descriptions, and approval status
- **SpeechSegment** table: Speech content tied to characters, with editing and approval tracking
- **AIGenerationRun** table: Explicit tracking of generation attempts (cast discovery, script split, annotation, render planning, audio generation)
- **RenderSegment** table: Individual segment render attempts under a generation run, with provider request/response tracking
- **Enhanced audiobook_project** table: Now includes `sourceText`, `languageCode`, `modelName`, `audioEncoding`, and `revision` fields
- **Updated audio_asset** table: Simplified type enum (VOICE_PREVIEW, SEGMENT_AUDIO, FULL_AUDIOBOOK) and linked to generation runs

**New API Endpoints:**
- `GET /audiobooks/{projectId}/library` — Complete audiobook library display with project metadata, characters, segments, generation history, and assets
- `GET /audiobooks/{projectId}/characters` — List project cast
- `POST /audiobooks/{projectId}/characters` — Add character with voice assignment
- `GET /audiobooks/{projectId}/segments` — Ordered segment list with character details
- `POST /audiobooks/{projectId}/segments` — Add speech segment
- `GET /audiobooks/{projectId}/generation-runs` — Generation attempt history
- `GET /audiobooks/{projectId}/generation-runs/{runId}` — Run detail with all render segments

**Frontend:**
- New `AudiobookLibraryDisplayComponent` for viewing complete project metadata, character casting, segment details, and generation history
- Updated types and service methods for new schema
- Backward compatibility maintained for existing audiobook-library and audiobook-studio pages

**Deployment Note:**
When deploying to production, back up your database before the migration. Flyway V6 performs a big-bang schema replacement and does not preserve existing audiobook data (intentional for this learning project). After the migration, the audiobook library endpoints will reflect the new schema.

---

### Audiobook Studio UX/UI Refinement

The Audiobook Studio MVP was upgraded from an internal workflow page into a more premium, cinematic AI audiobook studio experience:

- `/audiobook-studio` now opens with a frontend-only hero section: “Give every character in your story a voice.”
- The first viewport shows the intended product promise visually: pasted story text flows into a detected cast card and an audio waveform preview.
- The hero includes `Create audio story` and `Listen to demo` actions; the primary CTA scrolls to and focuses the existing story textarea.
- A new “From plain text to performed story” section explains the four-step journey: paste story, discover cast, direct performance, generate audio.
- The existing functional workflow remains below the motivational sections and still uses the same Angular component state and backend APIs.
- Detected cast cards now feel more like creative character/voice cards, with initials, stronger hierarchy, voice badges, and subtle per-card accent glows.
- Technical production fields such as language code, model name, and audio encoding are tucked behind `Advanced production settings`, while the story direction stays visible.
- This was a frontend-only UX/UI pass. No backend endpoints, database tables, provider behavior, Helm config, or business logic changed.
- Verification run for this chat: `npm run build`, `npm run test -- --watch=false --browsers=ChromeHeadless`, and `npm run test:e2e -- e2e/audiobook-studio.spec.ts`.

## Repo-Onboarding (kurzer Config-Block)

Für ein neues Repository muss nur ein kleiner Satz an Variablen gesetzt werden (statt Shell-Logik zu ändern):

```text
# GitHub Actions Repository Variables (Settings → Secrets and variables → Actions)
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
  - `/` → Frontend Service
  - `/api` → Backend Service
  - `/oauth2` → Backend Service
  - `/login/oauth2` → Backend Service
  - `/logout` → Backend Service
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
3. Sonderzeichen → `-`
4. Mehrfach-`-` reduzieren
5. führende/abschließende `-` entfernen
6. max. 10 Zeichen
7. falls abgeschnittenes Ende `-` ist: entfernen

Beispiel:

- `feature/codex-k3s-ganz-viel-mehr-text` → `codex-k3s`

## CI/CD (GitHub Actions)

Workflow: `.github/workflows/deploy.yml`

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

Der lokale E2E-Job läuft mit `E2E_BASE_URL=http://127.0.0.1:4200` und `E2E_USE_LOCAL_SERVERS=true` (der Standard wäre ebenfalls lokal), startet also Backend und Frontend über die bestehende Playwright-`webServer`-Konfiguration. Er enthält weiterhin deterministische UI-Tests mit gemockten Backend-Routen und zusätzlich `real-backend-health.spec.ts`. Dieser reale Integrationscheck lädt das lokale Frontend und ruft aus dem Browser-Kontext `GET /api/health` auf. Die Route ist bewusst stabil, benötigt keine Anmeldung, keine CSRF-Token und keine externen Provider-Secrets. Der Test schlägt fehl, wenn der Browser das lokal gestartete Backend nicht erreicht, wenn die Antwort kein `200 {"status":"ok"}` ist, oder wenn das Frontend die Antwort nicht verarbeiten und anzeigen kann.

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

Empfohlene schnelle lokale/Codex-Checks sind Backend-Build/Unit-Tests, Frontend-Unit-Tests und Frontend-Builds. E2E-Tests sind lokal optional und sollen gezielt laufen, wenn eine Änderung End-to-End-Verhalten, Routing, Auth, Deployment-Verhalten oder mehrere App-Schichten betrifft.

```bash
cd backend
gradle build

cd ../frontend
CHROME_BIN="${CHROME_BIN:-/tmp/chrome-no-sandbox}" npm test
npm run build
```

Lokale E2E-Tests starten standardmäßig Backend und Frontend über Playwright:

```bash
cd frontend
npm run test:e2e
```

Die Playwright-Suite unterscheidet zwischen:

- gemockten UI-E2E-Tests (`text-length.spec.ts`, `tts-workbench.spec.ts`), die gezielt Backend-Routen mocken, um UI-Erfolg und UI-Fehler deterministisch zu prüfen;
- realen Frontend-Backend-E2E-Tests (`real-backend-health.spec.ts`), die die geprüfte Backend-Route nicht mocken und standardmäßig über die lokal gestarteten Playwright-Webserver laufen.

E2E gegen eine deployte Umgebung:

```bash
cd frontend
E2E_BASE_URL="https://<deployed-host>" E2E_USE_LOCAL_SERVERS=false npm run test:e2e
```

Wichtig: Obwohl E2E lokal/Codex optional ist, ist E2E in der CI/CD-Pipeline mandatory und läuft dort lokal im GitHub-Actions-Runner mit `E2E_USE_LOCAL_SERVERS=true`.

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


## Akzeptanzkriterien (Textlänge)

The frontend calls `POST /api/projects/text-length/calculate`. For backward compatibility, `POST /api/text-length` remains supported with the same behavior.

Bewusst unterstützte Fälle für both Text Length endpoints:

- Leerer Text (`""`) liefert `length = 0`.
- Unicode-Eingaben (z. B. Umlaute/Emoji) werden akzeptiert und gezählt.
- Große Inputs (z. B. 10.000 Zeichen) werden verarbeitet.
- Ungültige JSON-Payloads werden mit HTTP `400 Bad Request` und strukturierter Fehlerantwort abgelehnt.
- Fehlende `text`-Property wird wie `null` behandelt und liefert `length = 0`.

## Health endpoints

- `GET /health` is the backend pod health endpoint used by Kubernetes probes.
- `GET /api/health` is protected like the rest of the API surface.

## API error responses

Backend API failures use a structured, frontend-safe JSON response:

```json
{
  "status": 502,
  "code": "TTS_WORKBENCH_PROVIDER_FAILED",
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
- The authenticated app uses a shared header and client-side routes: `/` for the landing page, `/audiobook-studio` for the Audiobook Studio MVP, `/text-length` for the existing text-length UI, and `/tts-workbench` for the TTS Workbench speaker/voice analysis MVP. Unknown frontend routes redirect to `/`.
- Only authenticated users see the routed app pages and chatbot widget.
- Unauthenticated users see only the sign-in UI, which starts OAuth via `/oauth2/authorization/google`.
- Logged-in users also see their auth state in the header and a logout button that calls `/logout` and returns to `/`.


## Audiobook Studio MVP

Audiobook Studio is a user-friendly frontend flow built on top of the existing TTS Workbench endpoints. It is available at `/audiobook-studio` and reframes the same pipeline as story input, cast discovery, script preview, performance notes, an audio production plan, and generated audio.

The MVP does not add database tables or new backend endpoints. It reuses the existing speaker analysis, speaker split, emotion annotation, final request preview, single-speaker render plan, and audio creation APIs while presenting story-focused language and a dark cinematic studio interface.

The page now starts with a product-led landing/workflow layer:

- A premium hero with the headline “Give every character in your story a voice.”
- A static visual demo that shows story text transforming into a detected cast and an audio waveform.
- Benefit chips for `Multi-speaker`, `Scene detection`, `Voice previews`, and `Export MP3`.
- A four-card “From plain text to performed story” journey section.
- Hero CTAs that keep the existing workflow reachable: `Create audio story` focuses the story input, and `Listen to demo` loads the sample story before focusing the textarea.

The page now includes a frontend-only review and correction layer before generation:

- Cast cards support `Edit`, `Save`, and `Cancel` for `speakerName`, `roleDescription`, and `voiceSuggestion`.
- Speaker names are formatted for display while preserving their original backend/internal value unless saved by the user.
- Script turns support one-at-a-time editing for `speaker` and `text`.
- Script approval is required before performance notes can be generated.
- Editing the script after performance notes exist marks those notes stale and blocks audio production planning until notes are regenerated.
- These review states are local component state only; no persistence, auth, deployment, database, provider, or Helm behavior changed.

## TTS Workbench (MVP)

The TTS Workbench page is a step-by-step development workbench for inspecting the intermediate data that will later feed a text-to-speech provider. It currently supports:

1. Raw dialogue input
2. Speaker and voice suggestions
3. Speaker split preview
4. Emotion annotation preview with simple markup such as `[happy]`, `[sad]`, `[calm]`, `[urgent]`, `[sigh]`, `[short pause]`, and `[medium pause]`
5. Final request JSON preview
6. Single-speaker render plan preview that groups only consecutive turns from the same speaker and outputs provider-shaped render requests

Backend endpoints:

- `POST /api/projects/tts-workbench/speaker-voice-analysis` with raw dialogue returns suggested rows containing `speakerName`, `roleDescription`, and `voiceSuggestion`.
- `POST /api/projects/tts-workbench/speaker-split-analysis` with raw dialogue and speaker suggestions returns `turns` containing `speaker` and `text`.
- `POST /api/projects/tts-workbench/emotion-annotation-analysis` with split turns returns annotated `turns` containing `speaker` and marked-up `text`.
- `POST /api/projects/tts-workbench/final-request-preview` with prompt, speakers, annotated turns, language code, model name, and audio encoding returns the final provider request JSON preview.
- `POST /api/projects/tts-workbench/single-speaker-render-plan` with the final request JSON returns `renderRequests`, where each item is provider-shaped JSON containing `input.text`, `voice.languageCode`, `voice.name`, `voice.modelName`, and `audioConfig.audioEncoding`.
- `POST /api/projects/tts-workbench/create-audio` with the step 6 `renderRequests` returns a downloadable MP3 for one render request or one concatenated MP3 for multiple requests. The UI keeps the full-plan button and also shows a per-render-request **Create audio** button. A per-request button sends only that one render request and downloads a filename such as `tts-render-request-2.mp3`; the full-plan flow downloads `tts-render-request-1.mp3` for a single request or `tts-render-plan.mp3` for multiple requests.

Single-speaker render requests intentionally do not return internal planning metadata such as turn indexes or speaker aliases. The preview JSON matches the provider request shape, for example:

```json
{
  "input": {
    "text": "[calm]The rain had turned the windows silver by the time they reached the old station café.\n[serious]Mara folded the letter twice, then unfolded it again."
  },
  "voice": {
    "languageCode": "en-US",
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

Prompts are accessed through a `TtsWorkbenchPromptProvider` abstraction. The current implementation returns static defaults, but the service structure is intentionally open for future prompts loaded from configuration, a database, an admin UI, project settings, or tenant-specific settings.

Automated tests use mock behavior and do not call Gemini APIs.

## Chatbot (MVP)

The frontend now includes a reusable chatbot widget component that calls `POST /api/chat` on the backend. The frontend never calls Gemini directly.

### Helm/runtime configuration

- `chat.geminiModel` controls the Gemini model (`gemini-2.5-flash` by default).
- `chat.provider` controls backend runtime provider (`gemini` or `mock`); the chart default is `mock` so local/feature-style installs do not require `GEMINI_API_KEY`.
- `chat.realProviderOnFeatureBranches` defaults to `false` and is used by the deploy workflow to keep feature branches in mock chatbot mode by default.
- `ttsWorkbench.googleCredentialsSecretName` controls the optional Kubernetes secret that provides `TTS_GOOGLE_SERVICE_ACCOUNT_JSON_B64` to the backend. Leave it empty to run Gemini chat without Google Cloud TTS credentials.
- `ttsWorkbench.googleCredentialsChecksum` is written to the backend pod template annotation so a Google TTS credential change creates a new backend ReplicaSet.
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


## Audiobook Library v2 Data Model

The audiobook library feature now supports detailed library displays showing complete audiobook creation metadata through a redesigned data model.

### Schema Overview

The new schema separates concerns across five core entities:

**AudiobookProject** — Top-level audiobook project container
- Fields: `id`, `userId`, `title`, `sourceText`, `languageCode`, `modelName`, `audioEncoding`, `status`, `revision`, `createdAt`, `updatedAt`
- Tracks the original story text, target language, AI model used, and audio format

**Character** — Cast member with voice assignment
- Fields: `id`, `projectId`, `name`, `roleDescription`, `voiceKey`, `sortOrder`, `approved`, `createdAt`, `updatedAt`
- Enables separate character casting from voice assignment
- Tracks voice selection and character approval status

**SpeechSegment** — Dialogue or narration segment
- Fields: `id`, `projectId`, `characterId`, `sequenceNo`, `originalText`, `annotatedText`, `edited`, `approved`, `createdAt`, `updatedAt`
- Tied to a specific character
- Supports editing and approval workflow
- Stores both original and emotion-annotated text

**AIGenerationRun** — Generation attempt tracking
- Fields: `id`, `projectId`, `type` (RunType enum), `status` (RunStatus enum), `requestJson`, `responseJson`, `errorMessage`, `startedAt`, `completedAt`, `createdAt`
- Tracks five run types: `CAST_DISCOVERY`, `SCRIPT_SPLIT`, `ANNOTATION`, `RENDER_PLAN`, `AUDIO_GENERATION`
- Tracks five statuses: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `STALE`
- Stores full request/response payloads for debugging and recovery

**RenderSegment** — Individual segment render attempt
- Fields: `id`, `aiGenerationRunId`, `speechSegmentId`, `text`, `providerRequestJson`, `status` (RunStatus enum), `audioAssetId`, `createdAt`, `startedAt`, `completedAt`
- Links speech segments to audio assets through generation runs
- Stores provider-specific request JSON for traceability

**AudioAsset** — Generated audio file
- Fields: `id`, `projectId`, `aiGenerationRunId`, `type` (AudioAssetType enum), `fileName`, `storageKey`, `contentType`, `durationMs`, `sizeBytes`, `createdAt`
- Simplified type enum: `VOICE_PREVIEW`, `SEGMENT_AUDIO`, `FULL_AUDIOBOOK`
- Tracks duration in milliseconds instead of seconds

### API Endpoints

**Project Management**
- `GET /audiobooks` — List all projects for authenticated user
- `GET /audiobooks/{projectId}` — Project metadata
- `POST /audiobooks` — Create new project
- `PUT /audiobooks/{projectId}` — Update project status/metadata

**Character Management**
- `GET /audiobooks/{projectId}/characters` — List cast
- `POST /audiobooks/{projectId}/characters` — Add character
- `PUT /audiobooks/{projectId}/characters/{characterId}` — Update character
- `DELETE /audiobooks/{projectId}/characters/{characterId}` — Remove character

**Segment Management**
- `GET /audiobooks/{projectId}/segments` — Ordered segment list
- `POST /audiobooks/{projectId}/segments` — Add segment
- `PUT /audiobooks/{projectId}/segments/{segmentId}` — Update segment

**Generation & Library Display**
- `POST /audiobooks/{projectId}/generate` — Start generation run
- `GET /audiobooks/{projectId}/generation-runs` — List generation attempts
- `GET /audiobooks/{projectId}/generation-runs/{runId}` — Run detail with render segments
- `GET /audiobooks/{projectId}/library` — **Complete library display** with project metadata, characters, segments, generation history, and assets

### Library Display Response

The `/library` endpoint returns a comprehensive view of the audiobook project:

```json
{
  "project": {
    "id": "...",
    "title": "The Amber Signal",
    "sourceText": "Once upon a time...",
    "languageCode": "en-US",
    "modelName": "gpt-4",
    "audioEncoding": "mp3",
    "status": "AUDIO_READY",
    "revision": 1
  },
  "characters": [
    {
      "id": "...",
      "name": "Alice",
      "roleDescription": "Protagonist, brave explorer",
      "voiceKey": "google-neural:en-US-Neural2-A",
      "sortOrder": 1,
      "approved": true
    }
  ],
  "segments": [
    {
      "id": "...",
      "characterId": "...",
      "characterName": "Alice",
      "sequenceNo": 1,
      "originalText": "Hello world",
      "annotatedText": "[calm] Hello world",
      "approved": true
    }
  ],
  "generationRuns": [
    {
      "id": "...",
      "type": "AUDIO_GENERATION",
      "status": "SUCCEEDED",
      "startedAt": "2026-05-10T10:00:00Z",
      "completedAt": "2026-05-10T10:05:00Z",
      "renders": [
        {
          "id": "...",
          "segmentId": "...",
          "status": "SUCCEEDED",
          "audioAsset": {
            "id": "...",
            "fileName": "segment_001.mp3",
            "durationMs": 5000
          }
        }
      ]
    }
  ],
  "assets": [
    {
      "id": "...",
      "type": "FULL_AUDIOBOOK",
      "fileName": "audiobook.zip",
      "durationMs": 360000
    }
  ]
}
```

### Frontend Components

**AudiobookLibraryDisplayComponent** (`audiobook-library-display.component.ts`)
- Loads complete library data via `AudiobookLibraryService.getLibraryDisplay(projectId)`
- Displays project metadata in header with statistics (character count, segment count, total duration)
- Shows cast list with character names, role descriptions, and voice assignments
- Displays segments table with character associations and approval status
- Renders generation run history with timestamps and render segment details
- Shows all audio assets with types and durations
- Mobile-responsive grid layout with color-coded status badges

### Deployment Considerations

**Database Migration:**
- Flyway V6 migration (`V6__migrate_audiobook_model.sql`) performs a big-bang schema replacement
- Old tables (`audiobook_project`, `audiobook_scene`, `audio_asset`) are dropped
- Existing audiobook data is not preserved (intentional for this learning project)
- **Before deploying to production**: Back up your database and verify the backup succeeded

**Production Deployment Steps:**
1. Back up production database
2. Deploy application with V6 migration
3. Flyway automatically runs V6 on startup
4. Old audiobook endpoints will 404 until data is migrated to new schema
5. New library display endpoints are immediately available

**Multi-tenancy:**
- All repository queries filter by `userId` for security
- Users can only access their own projects, characters, and segments
- Generation runs and assets inherit project ownership

### Testing

The migration includes comprehensive test coverage:

- **31 integration tests** in `AudiobookRepositoryIntegrationTest.java` covering all CRUD operations
- **Updated unit tests** for controller, service, and metadata calculator
- **All 127 backend tests pass**
- **All 19 E2E tests pass**

Run tests locally:

```bash
cd backend
gradle build

cd ../frontend
CHROME_BIN="${CHROME_BIN:-/tmp/chrome-no-sandbox}" npm test
npm run build

cd ../
npm run test:e2e
```


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
