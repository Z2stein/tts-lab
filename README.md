# tts-lab

Ein einfaches Lernprojekt mit getrenntem Angular-Frontend und Java-Spring-Backend.

## Ziel

Flow: **Textfeld → Submit → Backend gibt Textlänge zurück**.

## Projektstruktur

- `frontend/` → Angular App (npm + Angular CLI)
- `backend/` → Spring Boot App (Java 21 + Gradle)
- `docker-compose.yml` → startet beide Container getrennt

## API

- `POST /api/text-length`
- Request:

```json
{ "text": "Hallo" }
```

- Response:

```json
{ "length": 5 }
```

## Lokal starten (ohne Docker)

### Backend

```bash
cd backend
gradle bootRun
```

Backend läuft auf `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm start
```

Frontend läuft auf `http://localhost:4200`.

Für lokales Angular-Dev-Setup kannst du in `frontend/src/app/app.component.ts` bei Bedarf direkt auf `http://localhost:8080/api/text-length` zeigen.

## Mit Docker starten

```bash
docker compose up --build
```

Dann erreichbar unter:

- Frontend: `http://localhost:8080`
- Backend intern über Docker-Netzwerk (`backend:8080`)

## Backend-Tests + Abdeckung

```bash
cd backend
gradle test jacocoTestReport
```

Coverage-Report (HTML):

- `backend/build/reports/jacoco/test/html/index.html`

## Deployment (GitHub Actions)

Der Workflow `.github/workflows/deploy.yml` baut und pusht **zwei Images** nach GHCR:

- `ghcr.io/<owner>/tts-lab-frontend:<tag>`
- `ghcr.io/<owner>/tts-lab-backend:<tag>`

Auf dem Hetzner-Server werden beide Services über `compose.app.yaml` als gemeinsamer Stack gestartet; Traefik routet auf den Frontend-Service.

Vor dem Image-Push führt GitHub Actions einen Frontend-Build plus Smoke-Check aus (`scripts/frontend_smoke_check.sh`), damit fehlende Browser-Bundles wie `polyfills.js` früh auffallen.
