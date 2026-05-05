# tts-lab

Lernprojekt mit Angular-Frontend und Spring-Boot-Backend.

## Repo-Onboarding (kurzer Config-Block)

Für ein neues Repository muss nur ein kleiner Satz an Variablen gesetzt werden (statt Shell-Logik zu ändern):

```text
# GitHub Actions Repository Variables (Settings → Secrets and variables → Actions)
APP_SLUG=<kebab-case-app-name>        # optional, default: Repository-Name
HETZNER_HOST=<server-ip-or-hostname>  # required
```

Naming-Konventionen:

- `APP_SLUG` in `kebab-case` (z. B. `my-tts-app`).
- Aus `APP_SLUG` werden automatisch abgeleitet:
  - Namespaces/Releases: `<app-slug>`, `<app-slug>-dev`, `<app-slug>-<branch-slug>`
  - Hosts: `<app-slug>.<server-ip>.sslip.io`, `dev.<app-slug>...`, `<branch-slug>.<app-slug>...`
  - GHCR-Images: `<app-slug>-backend`, `<app-slug>-frontend`
- Backward Compatibility: Wenn `APP_SLUG` fehlt oder leer ist, fällt der Workflow auf den Repository-Namen zurück; die Deployment-Skripte nutzen als letzte Fallback-Stufe `tts-lab`.

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
  - URL: `https://<app-slug>.178.105.41.67.sslip.io`
- `develop`
  - Namespace: `<app-slug>-dev`
  - Release: `<app-slug>-dev`
  - URL: `https://dev.<app-slug>.178.105.41.67.sslip.io`
- Feature-Branches
  - Namespace: `<app-slug>-<branch-slug>`
  - Release: `<app-slug>-<branch-slug>`
  - URL: `https://<branch-slug>.<app-slug>.178.105.41.67.sslip.io`

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
6. Namespace idempotent anlegen/aktualisieren
7. `ghcr-pull-secret` idempotent im Namespace anlegen/aktualisieren
8. `helm upgrade --install` ausführen

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


## Akzeptanzkriterien (Textlänge)

Bewusst unterstützte Fälle für `POST /api/text-length`:

- Leerer Text (`""`) liefert `length = 0`.
- Unicode-Eingaben (z. B. Umlaute/Emoji) werden akzeptiert und gezählt.
- Große Inputs (z. B. 10.000 Zeichen) werden verarbeitet.
- Ungültige JSON-Payloads werden mit HTTP `400 Bad Request` abgelehnt.
- Fehlende `text`-Property wird wie `null` behandelt und liefert `length = 0`.

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
- Only authenticated users see the real app controls.
- Unauthenticated users see only the sign-in UI, which starts OAuth via `/oauth2/authorization/google`.
- Logged-in users also see a logout button that calls `/logout` and returns to `/`.

## Chatbot (MVP)

The frontend now includes a reusable chatbot widget component that calls `POST /api/chat` on the backend. The frontend never calls Gemini directly.

### Helm/runtime configuration

- `chat.geminiModel` controls the Gemini model (`gemini-2.5-flash` by default).
- `chat.provider` controls backend runtime provider (`gemini` or `mock`).
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
