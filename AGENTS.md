# AGENTS.md

Projektregeln für zukünftige Codex-Aufgaben in diesem Repository.

## Ziel des Projekts

Dieses Repository ist ein bewusst einfaches Lernprojekt für:

1. Eine minimal verständliche Fullstack-App (Angular + Spring Boot)
2. Container-Builds mit Docker
3. Deployment mit GitHub Actions auf einen Hetzner-Server
4. Runtime-Orchestrierung über k3s + Helm

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

## Deployment-Konventionen

- GitHub Actions Workflow liegt unter `.github/workflows/deploy.yml`.
- Primärer Deployment-Weg ist **k3s + Helm**.
- Zielserver-Pfad für Chart-Dateien ist `/opt/tts-lab`.
- Deployments laufen über `helm upgrade --install` via SSH auf dem Server.
- Docker Compose und Docker-Traefik sind nicht mehr der Runtime-Deployment-Weg.

## Änderungsvorgehen für Codex

1. Bestehende Dateien minimal anpassen statt groß umbauen.
2. README bei jeder relevanten Verhaltensänderung aktualisieren.
3. Neue Abhängigkeiten nur mit kurzer Begründung einführen.
4. Sicherheitsrelevante Änderungen (Secrets, SSH, Registry) besonders sorgfältig behandeln.
