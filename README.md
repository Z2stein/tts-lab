# tts-lab

Minimales Lernprojekt: Node.js + Express mit Docker und automatischem Deployment auf einen Hetzner-Server via GitHub Actions.

## Features

- `GET /` → einfache Angular-Landing-Page mit `Hello World`
- `GET /health` → `{ "status": "ok" }`
- App läuft standardmäßig auf Port `80` im Container
- Docker-Image wird nach GitHub Container Registry (GHCR) gepusht
- Deployment läuft automatisch bei Push auf `main`, `develop` und Feature-Branches
- Hostbasiertes Routing über Traefik + `sslip.io`

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

### Branch-Regeln

- `main` = Production
- `develop` = Staging
- alle anderen Branches = Preview

### Routing-Regeln mit `sslip.io`

Die öffentliche IP wird aus dem GitHub Secret `HETZNER_HOST` genutzt.

- `main` → `main.<HETZNER_HOST>.sslip.io`
- `develop` → `develop.<HETZNER_HOST>.sslip.io`
- `feature/x-y` → `<branch-slug>.<HETZNER_HOST>.sslip.io`

Slug-Regeln für Branches:

- alles lowercase
- alle Zeichen außer `a-z` und `0-9` werden zu `-`
- aufeinanderfolgende `-` werden zusammengefasst
- führende/abschließende `-` werden entfernt

### Compose-Struktur auf dem Server

Ablagepfad: `/opt/tts-lab`

- `compose.traefik.yaml` → dauerhafter Reverse Proxy (Port `80`)
- `compose.app.yaml` → App-Stack je Branch
- `scripts/deploy_stack.sh` → Upsert-Deployment (pull + up)
- `scripts/remove_stack.sh` → Cleanup für Preview-Stacks

Isolation pro Branch:

- Über `COMPOSE_PROJECT_NAME=tts-<branch-slug>` läuft jeder Branch als eigener Compose-Stack.
- Die App veröffentlicht keinen Host-Port mehr; Traefik routet intern per Docker-Netzwerk.

Image-Tags:

- branch+commit-spezifisch: `<branch-slug>-<short-sha>`
- branch-spezifisch rolling: `<branch-slug>-latest`
- Nach erfolgreichem Deployment steht die vollqualifizierte, klickbare URL in der GitHub Actions Job Summary.

## Benötigte GitHub Secrets

- `HETZNER_HOST` (öffentliche Server-IP)
- `HETZNER_USER`
- `HETZNER_SSH_KEY`

## Deploy verwenden

### `main` deployen (Production)

```bash
git checkout main
git push origin main
```

Erreichbar unter:

- `http://main.<HETZNER_HOST>.sslip.io`

### `develop` deployen (Staging)

```bash
git checkout develop
git push origin develop
```

Erreichbar unter:

- `http://develop.<HETZNER_HOST>.sslip.io`

### Feature-Branch deployen (Preview)

Beispielbranch: `feature/add-banner`

```bash
git checkout -b feature/add-banner
# Änderungen committen
git push -u origin feature/add-banner
```

Erreichbar unter (Beispiel-Slug `feature-add-banner`):

- `http://feature-add-banner.<HETZNER_HOST>.sslip.io`

## Cleanup-Konzept für Branch-Stände

Automatisch:

- Wenn ein Feature-Branch auf GitHub gelöscht wird, löst das `delete`-Event im Workflow einen Cleanup aus.
- Der zugehörige Compose-Stack `tts-<branch-slug>` wird auf dem Server gestoppt und entfernt.

Manuell (falls nötig):

```bash
ssh <user>@<host>
cd /opt/tts-lab
COMPOSE_PROJECT_NAME=tts-<branch-slug> ./scripts/remove_stack.sh
```

Hinweis:

- `main` und `develop` werden beim Cleanup bewusst übersprungen.
