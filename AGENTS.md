# AGENTS.md

Projektregeln für zukünftige Codex-Aufgaben in diesem Repository.

## Ziel des Projekts

Dieses Repository ist ein bewusst einfaches Lernprojekt für:

1. Eine minimale Express-Web-App
2. Containerisierung mit Docker
3. Deployment mit GitHub Actions auf einen Hetzner-Server

## Leitlinien

- Keep it simple: Änderungen möglichst klein und nachvollziehbar halten.
- Keine unnötige Komplexität oder zusätzliche Infrastruktur.
- Fokus auf Lernbarkeit vor "Production-Optimierung".

## Technischer Rahmen

Nicht hinzufügen, außer es wird ausdrücklich angefordert:

- OpenAI API
- Text-to-Speech
- Datenbank
- Authentifizierung
- Kubernetes/Helm

## Code-Konventionen

- Node.js mit CommonJS (`require`, `module.exports`) beibehalten.
- Endpunkte in `server.js` klar und kurz halten.
- Keine Framework-Migration ohne explizite Anforderung.

## Deployment-Konventionen

- GitHub Actions Workflow liegt unter `.github/workflows/deploy.yml`.
- Deployment-Trigger: Push auf `main`.
- Zielserver-Pfad ist `/opt/tts-lab`.
- Docker Compose Befehle müssen auf dem Server via SSH ausgeführt werden.

## Änderungsvorgehen für Codex

1. Bestehende Dateien minimal anpassen statt groß umbauen.
2. README bei jeder relevanten Verhaltensänderung aktualisieren.
3. Neue Abhängigkeiten nur mit kurzer Begründung einführen.
4. Sicherheitsrelevante Änderungen (Secrets, SSH, Registry) besonders sorgfältig behandeln.
