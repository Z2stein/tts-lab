# tts-lab

Lernprojekt mit Angular-Frontend und Spring-Boot-Backend.

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
- Backend-Alias-Service `backend` bleibt standardmäßig aktiv für `http://backend:8080` im Frontend-Container.

## Ziel-Umgebungen

- `main`
  - Namespace: `tts-lab`
  - Release: `tts-lab`
  - URL: `http://tts-lab.178.105.41.67.sslip.io`
- `develop`
  - Namespace: `tts-lab-dev`
  - Release: `tts-lab-dev`
  - URL: `http://dev.tts-lab.178.105.41.67.sslip.io`
- Feature-Branches
  - Namespace: `tts-lab-<branch-slug>`
  - Release: `tts-lab-<branch-slug>`
  - URL: `http://<branch-slug>.tts-lab.178.105.41.67.sslip.io`

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

Pull Requests:

1. Nur Validierung (Docker-Build für Backend + Frontend ohne Push/Deploy)
2. Kein Helm-Deploy bei PR-Events

Ablauf bei Push:

1. Branch-Typ erkennen (main/develop/feature)
2. Slug, Namespace, Release, Host berechnen
3. Frontend/Backend Image bauen
4. Images nach GHCR pushen
5. SSH auf Hetzner
6. Namespace idempotent anlegen/aktualisieren
7. `ghcr-pull-secret` idempotent im Namespace anlegen/aktualisieren
8. `helm upgrade --install` ausführen

Parallelität:

- Pro Branch/PR läuft nur ein Workflow gleichzeitig (`concurrency` mit Cancel-In-Progress).
- Dadurch keine überlappenden Deployments für denselben Branch.

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
