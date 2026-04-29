# tts-lab

Ein einfaches Lernprojekt mit getrenntem Angular-Frontend und Java-Spring-Backend.

## Ziel

Flow: **Textfeld → Submit → Backend gibt Textlänge zurück**.

## Projektstruktur

- `frontend/` → Angular App (npm + Angular CLI)
- `backend/` → Spring Boot App (Java 21 + Gradle)
- `docker-compose.yml` → startet beide Container getrennt
- `deploy/helm/tts-lab/` → Helm-Chart für manuelles k3s-Deployment

## API

- `GET /health` -> `{ "status": "ok" }`
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

## Manuelles Deployment auf Hetzner mit k3s + Helm + Traefik

Fokus: bewusst manuell, ohne GitHub Actions.

### 1) Voraussetzungen auf dem Server

- k3s installiert (`kubectl get nodes` funktioniert)
- Traefik in k3s aktiv (Standard bei k3s)
- Helm 3 installiert
- Domain zeigt auf den Server (z. B. `tts.example.com`)
- TLS-Secret im Ziel-Namespace vorhanden (oder später erstellen)

### 2) Images bauen und Registry bereitstellen

Du brauchst ein Frontend- und Backend-Image in einer erreichbaren Registry.

Beispiel (lokal):

```bash
docker build -t ghcr.io/<owner>/tts-lab-frontend:<tag> ./frontend
docker build -t ghcr.io/<owner>/tts-lab-backend:<tag> ./backend
docker push ghcr.io/<owner>/tts-lab-frontend:<tag>
docker push ghcr.io/<owner>/tts-lab-backend:<tag>
```

### 3) Helm Values für Server anpassen

`deploy/helm/tts-lab/values.yaml` enthält Defaults. Für produktive Nutzung mindestens setzen:

- `frontend.image`, `frontend.tag`
- `backend.image`, `backend.tag`
- `ingress.host`
- `ingress.tlsSecretName`

Optional kannst du dafür eine eigene Datei (z. B. `values.prod.yaml`) nutzen.

### 4) Deployment ausführen

Auf dem Hetzner-Server:

```bash
helm upgrade --install tts-lab ./deploy/helm/tts-lab \
  --namespace tts-lab \
  --create-namespace \
  -f ./deploy/helm/tts-lab/values.yaml
```

### 5) Status prüfen

```bash
kubectl -n tts-lab get pods
kubectl -n tts-lab get svc
kubectl -n tts-lab get ingressroute
```

Wenn alles läuft, sollte die App unter `https://<ingress.host>` erreichbar sein.

### 6) Update/Rollback

Update mit neuen Tags (Values anpassen + gleicher Helm-Befehl).

Rollback bei Bedarf:

```bash
helm -n tts-lab history tts-lab
helm -n tts-lab rollback tts-lab <revision>
```

## Frontend E2E-Test (Playwright)

```bash
cd frontend
npm ci
npx playwright install --with-deps chromium
npm run test:e2e
```

Der Test deckt den kompletten Flow ab:

1. Seite öffnen
2. Text eingeben
3. Submit klicken
4. Auf Ergebnis warten
5. Ergebnis validieren

## Backend-Tests + Abdeckung

```bash
cd backend
gradle test jacocoTestReport
```

Coverage-Report (HTML):

- `backend/build/reports/jacoco/test/html/index.html`
