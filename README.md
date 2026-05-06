# tts-lab

Lernprojekt mit Angular-Frontend und Spring-Boot-Backend.

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
- Standard-Health-Probes im Helm-Chart:
  - Frontend: `GET /`
  - Backend: `GET /health`
- Routing:
  - `/` → Frontend Service
  - `/api` → Backend Service, including the unauthenticated `GET /api/health` endpoint used by real deployed E2E checks
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
5. Mandatory Playwright-E2E-Tests im separaten Job `predeploy-e2e` ausführen:
   - temporären kind-Cluster in GitHub Actions erstellen
   - dasselbe Helm-Chart mit den gerade gebauten Images deployen
   - CI-sichere Konfiguration verwenden (`auth.mode=mock`, `chat.provider=mock`, kein Hetzner-SSH, keine Production-Secrets, keine Production-Daten)
   - Backend- und Frontend-Readiness per Helm/Kubernetes abwarten
   - Frontend lokal per Port-Forward verfügbar machen
   - Playwright mit `E2E_BASE_URL=http://127.0.0.1:8080` und `E2E_USE_LOCAL_SERVERS=false` ausführen
6. Nur wenn `predeploy-e2e` erfolgreich war: SSH auf Hetzner
7. Namespace idempotent anlegen/aktualisieren
8. `ghcr-pull-secret` idempotent im Namespace anlegen/aktualisieren
9. `helm upgrade --install --wait --timeout 5m` ausführen
10. Backend- und Frontend-Deployments per `kubectl rollout status` abwarten
11. Backend- und Frontend-Pods per `kubectl wait --for=condition=Ready pod -l ...` abwarten

Die Pipeline schlägt fehl, wenn Rollout/Pod-Readiness nicht erreicht wird oder wenn die E2E-Tests fehlschlagen. Feste Sleep-Zeiten sind nicht der primäre Synchronisationsmechanismus; die Pipeline nutzt Kubernetes-Readiness und die Helm-Chart-Probes (`GET /health` im Backend, `GET /` im Frontend). Schlägt `predeploy-e2e` fehl, wird der echte Hetzner-Deploy-Job durch die Job-Abhängigkeit nicht ausgeführt.

Die E2E-Stufe enthält weiterhin deterministische UI-Tests mit gemockten Backend-Routen und zusätzlich `deployed-real-backend.spec.ts`. Dieser reale Integrationscheck lädt das temporär deployte Frontend und ruft aus dem Browser-Kontext `GET /api/health` auf. Die Route ist bewusst stabil, benötigt keine Anmeldung, keine CSRF-Token und keine externen Provider-Secrets. Full E2E darf in CI/CD nicht gegen Production/Hetzner laufen; dafür nutzt die Pipeline ausschließlich die temporäre GitHub-Actions-Umgebung.

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
- realen Frontend-Backend-E2E-Tests (`deployed-real-backend.spec.ts`), die die geprüfte Backend-Route nicht mocken und eine vollständig deployte App-Umgebung erwarten.

E2E gegen eine nicht-produktive, bereits gestartete Umgebung:

```bash
cd frontend
E2E_BASE_URL="https://<non-production-host>" E2E_USE_LOCAL_SERVERS=false npm run test:e2e
```

Wichtig: Obwohl E2E lokal/Codex optional ist, ist E2E in der CI/CD-Pipeline mandatory. CI/CD führt Full E2E vor dem echten Deployment im Job `predeploy-e2e` aus. Full E2E soll nicht gegen Production/Hetzner laufen.


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
- `GET /api/health` returns the same `{ "status": "ok" }` payload through the public `/api` ingress route and is intentionally unauthenticated so deployed E2E can verify real frontend-to-backend connectivity without OAuth, CSRF, or external provider dependencies.

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
- The authenticated app uses a shared header and client-side routes: `/` for the landing page, `/text-length` for the existing text-length UI, and `/tts-workbench` for the TTS Workbench speaker/voice analysis MVP. Unknown frontend routes redirect to `/`.
- Only authenticated users see the routed app pages and chatbot widget.
- Unauthenticated users see only the sign-in UI, which starts OAuth via `/oauth2/authorization/google`.
- Logged-in users also see their auth state in the header and a logout button that calls `/logout` and returns to `/`.


## TTS Workbench (MVP)

The TTS Workbench page is a step-by-step development workbench for inspecting the intermediate data that will later feed a text-to-speech provider. It currently supports:

1. Raw dialogue input
2. Speaker and voice suggestions
3. Speaker split preview
4. Emotion annotation preview with simple markup such as `[happy]`, `[sad]`, `[calm]`, `[urgent]`, `[sigh]`, `[short pause]`, and `[medium pause]`
5. Final request JSON preview

Backend endpoints:

- `POST /api/projects/tts-workbench/speaker-voice-analysis` with raw dialogue returns suggested rows containing `speakerName`, `roleDescription`, and `voiceSuggestion`.
- `POST /api/projects/tts-workbench/speaker-split-analysis` with raw dialogue and speaker suggestions returns `turns` containing `speaker` and `text`.
- `POST /api/projects/tts-workbench/emotion-annotation-analysis` with split turns returns annotated `turns` containing `speaker` and marked-up `text`.
- `POST /api/projects/tts-workbench/final-request-preview` with prompt, speakers, annotated turns, language code, model name, and audio encoding returns the final provider request JSON preview.

Runtime behavior follows the existing chatbot provider mode where possible:

- `CHATBOT_PROVIDER=mock` returns deterministic local speaker suggestions, speaker splitting, emotion annotation, and final JSON preview data. It never calls Gemini.
- `CHATBOT_PROVIDER=gemini` asks the configured chat provider for structured speaker/voice, speaker split, and emotion annotation output. Provider failures or invalid provider output now return structured API errors so the frontend can show a clear failure instead of silently displaying fallback data.

Prompts are accessed through a `TtsWorkbenchPromptProvider` abstraction. The current implementation returns static defaults, but the service structure is intentionally open for future prompts loaded from configuration, a database, an admin UI, project settings, or tenant-specific settings.

Automated tests use mock behavior and do not call Gemini APIs.

## Chatbot (MVP)

The frontend now includes a reusable chatbot widget component that calls `POST /api/chat` on the backend. The frontend never calls Gemini directly.

### Helm/runtime configuration

- `chat.geminiModel` controls the Gemini model (`gemini-2.5-flash` by default).
- `chat.provider` controls backend runtime provider (`gemini` or `mock`); the chart default is `mock` so local/feature-style installs do not require `GEMINI_API_KEY`.
- `chat.realProviderOnFeatureBranches` defaults to `false` and is used by the deploy workflow to keep feature branches in mock chatbot mode by default.
- The frontend remains provider-agnostic and always calls `POST /api/chat`.

### Required secret

- `GEMINI_API_KEY` is required for `main` and `develop` deployments (provider = `gemini`).
- Feature branch deployments run with provider = `mock` by default, so `GEMINI_API_KEY` is not required in that default mode.
- If feature branches explicitly enable the real provider (`CHAT_REAL_PROVIDER_ON_FEATURE_BRANCHES=true` in GitHub Actions variables), then `GEMINI_API_KEY` is required there as well.
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


## Chatbot rate limiting (MVP)

Backend chat requests (`POST /api/chat`) are protected by a fixed-window request limiter (in-memory storage).

### Config

Spring env vars:

- `CHAT_LIMIT_ENABLED` (default `true`)
- `CHAT_LIMIT_WINDOW` (default `1h`)
- `CHAT_LIMIT_MAX_REQUESTS` (default `5`)
- `CHAT_LIMIT_ID_HEADER` (default `X-User-Id`)

Helm values:

```yaml
chatbot:
  rateLimit:
    enabled: true
    window: 1h
    maxRequests: 5
    idHeader: X-User-Id
```

Identity resolution order:

1. Authenticated principal id (`sub`)
2. Configured header (`CHAT_LIMIT_ID_HEADER`)
3. Client IP fallback

When exceeded, backend returns HTTP `429` with `Retry-After` and JSON:

```json
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Chat usage limit exceeded. Please try again later.",
  "retry_after": 1234,
  "limit": {
    "window": "PT1H",
    "max_requests": 5
  }
}
```

Limitations of current in-memory store:

- Works per pod only
- Counters are lost on restart
- Not consistent across multiple replicas

For multi-replica environments, Redis is the recommended next step (store interface is already separated).
