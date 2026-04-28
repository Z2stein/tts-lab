# tts-lab

Minimales Lernprojekt: Node.js + Express mit Docker und automatischem Deployment auf einen Hetzner-Server via GitHub Actions.

## Features

- `GET /` → einfache Angular-Landing-Page mit `Hello World`
- `GET /health` → `{ "status": "ok" }`
- App läuft standardmäßig auf Port `80`
- Docker-Image wird nach GitHub Container Registry (GHCR) gepusht
- Deployment läuft automatisch bei Push auf `main`

## Lokaler Start

### 1) Voraussetzungen

- Node.js 20+
- npm
- Docker (optional für Container-Start)

### 2) App direkt mit Node starten

```bash
npm install
npm start
```

Danach erreichbar unter:
- http://localhost/
- http://localhost/health

Hinweis: Falls Port 80 lokal nicht erlaubt ist, starte mit einem alternativen Port:

```bash
PORT=3000 npm start
```

### 3) App mit Docker starten

```bash
docker build -t tts-lab:local .
docker run --rm -p 80:80 tts-lab:local
```

## Deployment-Ablauf (GitHub Actions → Hetzner)

Workflow-Datei: `.github/workflows/deploy.yml`

Bei jedem Push auf `main` passiert:

1. GitHub Action baut das Docker-Image.
2. Image wird nach `ghcr.io/<github-owner>/tts-lab` gepusht.
3. Action verbindet sich per SSH mit dem Hetzner-Server.
4. `compose.yaml` wird nach `/opt/tts-lab/compose.yaml` kopiert.
5. Auf dem Server werden ausgeführt:
   - `docker compose pull`
   - `docker compose up -d`

## Benötigte GitHub Secrets

- `HETZNER_HOST`
- `HETZNER_USER`
- `HETZNER_SSH_KEY`

## Server-Voraussetzungen

- Docker installiert
- Docker Compose verfügbar
- Zielpfad: `/opt/tts-lab`

## Hinweise

- Das Compose-File verwendet das Image `ghcr.io/${GHCR_OWNER}/tts-lab:latest`.
- Im Workflow wird `GHCR_OWNER` automatisch aus dem Repository-Owner gesetzt.
