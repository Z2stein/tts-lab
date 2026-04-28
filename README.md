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
