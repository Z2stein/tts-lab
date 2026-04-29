# tts-lab

Ein einfaches Lernprojekt mit getrenntem Angular-Frontend und Java-Spring-Backend.

## Ziel

Flow: **Textfeld → Submit → Backend gibt Textlänge zurück**.

## Projektstruktur

- `frontend/` → Angular App (npm + Angular CLI)
- `backend/` → Spring Boot App (Java 21 + Gradle)
- `docker-compose.yml` → startet beide Container getrennt

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

## Deployment (GitHub Actions)

Der Workflow `.github/workflows/deploy.yml` baut und pusht **zwei Images** nach GHCR:

- `ghcr.io/<owner>/tts-lab-frontend:<tag>`
- `ghcr.io/<owner>/tts-lab-backend:<tag>`

Auf dem Hetzner-Server werden beide Services über `compose.app.yaml` als gemeinsamer Stack gestartet; Traefik routet auf den Frontend-Service.

Vor dem Image-Push führt GitHub Actions automatisiert einen Playwright-E2E-Test sowie Frontend-Build + Smoke-Check aus (`scripts/frontend_smoke_check.sh`), damit UI-Regressionen und fehlende Browser-Bundles wie `polyfills.js` früh auffallen.

## Manuelles Kubernetes-/Helm-Deployment auf Hetzner (k3s)

Dieser Abschnitt ergänzt das bestehende Docker-/Compose-Deployment um einen **ersten manuellen Helm- und Kubernetes-Stand**. Das bestehende Docker-Setup und GitHub Actions bleiben unverändert.

### Was der Helm Chart erzeugt

Der Chart `charts/tts-lab` erstellt folgende Ressourcen:

- 2 Deployments:
  - Frontend (`...-frontend`)
  - Backend (`...-backend`)
- 2 Services (ClusterIP):
  - Frontend Service
  - Backend Service
- 1 Ingress (optional per `ingress.enabled`)

### Interne Verbindung Frontend ↔ Backend

- Beide Pods laufen im gleichen Kubernetes Namespace.
- Das Backend ist intern über seinen Service (`...-backend`) erreichbar.
- Externer Traffic läuft über den Ingress:
  - `/` → Frontend Service
  - `/api` → Backend Service

### Routing über Traefik

- Traefik läuft als Ingress Controller in Kubernetes.
- Die Ingress-Regeln im Chart leiten den Host (z. B. `main.<HETZNER_HOST>.sslip.io`) weiter:
  - `http://main.<HETZNER_HOST>.sslip.io/` an Frontend
  - `http://main.<HETZNER_HOST>.sslip.io/api/...` an Backend

### Wichtige Werte in `charts/tts-lab/values.yaml`

Bitte vor Deployment mindestens anpassen:

- `ingress.host`
- `frontend.image.repository`
- `frontend.image.tag`
- `backend.image.repository`
- `backend.image.tag`
- optional `imagePullSecrets` (z. B. `[{"name":"ghcr-pull-secret"}]`)

Beispiel-Host:

```yaml
ingress:
  host: main.1.2.3.4.sslip.io
```

### Schritt-für-Schritt: manuelles Deployment

1. **Docker-Traefik stoppen** (damit Ports 80/443 frei sind)

   Beispiel (abhängig von deinem Setup):

   ```bash
   docker ps
   docker stop <docker-traefik-container>
   ```

2. **Prüfen, ob Port 80 und 443 frei sind**

   ```bash
   sudo ss -ltnp | rg ':80|:443'
   ```

3. **Traefik Helm Repository hinzufügen und aktualisieren**

   ```bash
   helm repo add traefik https://traefik.github.io/charts
   helm repo update
   ```

4. **Traefik Namespace erstellen**

   ```bash
   kubectl create namespace traefik --dry-run=client -o yaml | kubectl apply -f -
   ```

5. **Traefik in k3s installieren**

   ```bash
   helm upgrade --install traefik traefik/traefik \
     --namespace traefik
   ```

6. **Traefik prüfen**

   ```bash
   kubectl get pods -n traefik
   kubectl get svc -n traefik
   ```

7. **Namespace für die App erstellen**

   ```bash
   kubectl create namespace tts-lab --dry-run=client -o yaml | kubectl apply -f -
   ```

8. **Optional: imagePullSecret anlegen**

   Beispiel für GHCR:

   ```bash
   kubectl create secret docker-registry ghcr-pull-secret \
     --namespace tts-lab \
     --docker-server=ghcr.io \
     --docker-username=<GHCR_USER> \
     --docker-password=<GHCR_TOKEN>
   ```

   Danach in `charts/tts-lab/values.yaml` setzen:

   ```yaml
   imagePullSecrets:
     - name: ghcr-pull-secret
   ```

9. **Helm Chart rendern (`helm template`)**

   ```bash
   helm template tts-lab ./charts/tts-lab \
     --namespace tts-lab
   ```

10. **Helm Chart prüfen (`helm lint`)**

   ```bash
   helm lint ./charts/tts-lab
   ```

11. **App deployen (`helm upgrade --install`)**

   ```bash
   helm upgrade --install tts-lab ./charts/tts-lab \
     --namespace tts-lab
   ```

12. **Pods prüfen**

   ```bash
   kubectl get pods -n tts-lab
   ```

13. **Services prüfen**

   ```bash
   kubectl get svc -n tts-lab
   ```

14. **Ingress prüfen**

   ```bash
   kubectl get ingress -n tts-lab
   ```

15. **Logs prüfen**

   ```bash
   kubectl logs -n tts-lab deploy/tts-lab-tts-lab-frontend --tail=100
   kubectl logs -n tts-lab deploy/tts-lab-tts-lab-backend --tail=100
   ```

16. **App im Browser testen**

   Beispiel:

   - `http://main.<HETZNER_HOST>.sslip.io/`
   - `http://main.<HETZNER_HOST>.sslip.io/api/health`

17. **Deployment wieder entfernen**

   ```bash
   helm uninstall tts-lab -n tts-lab
   ```

   Optional Namespace entfernen:

   ```bash
   kubectl delete namespace tts-lab
   ```

18. **Optional Docker-Traefik wieder starten**

   ```bash
   docker start <docker-traefik-container>
   ```
