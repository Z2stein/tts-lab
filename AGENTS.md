# AGENTS.md

Project rules for future Codex tasks in this repository.

## Project goal

This repository is a deliberately simple learning project for:

1. A minimally understandable fullstack app with Angular + Spring Boot
2. Container builds with Docker
3. Deployment with GitHub Actions to a Hetzner server
4. Runtime orchestration via k3s + Helm

## Core principles

- Keep it simple: changes must be small, understandable, and easy to review.
- Work test-driven whenever practical: first reproduce bugs or define expected behavior with a failing test, then implement the smallest fix.
- Prefer learning value over production-level optimization.
- Do not introduce unnecessary architecture, frameworks, infrastructure, or abstractions.
- Fix the actual cause of broken behavior instead of hiding symptoms.

## Single source of truth

- Every important value, rule, or behavior must have one clear source of truth.
- Do not duplicate configuration, business rules, deployment values, environment values, or derived logic across multiple files unless there is a clear reason.

## Automated quality assurance

Automated checks are mandatory. A change is not done if applicable fast checks cannot run.

### Recommended Codex/local checks

Run fast, non-Docker checks by default when the touched areas make them applicable:

1. Frontend unit tests
2. Backend unit tests / backend build
3. Frontend build
4. Linting or formatting checks if added to the project
5. Helm chart validation when chart files changed
6. README update when behavior, setup, deployment, or quality checks changed

### End-to-end tests in Codex/local workflow

End-to-end tests are optional for Codex/local agent execution. Do not treat E2E as a required local quality gate for every change, because they start the full application stack and are slower than unit/build checks.

Run E2E manually when a change affects cross-service behavior, routing, authentication flow, deployment-only behavior, or when the user explicitly asks for it:

```bash
cd frontend
npm run test:e2e
```

The Playwright suite contains two kinds of tests:

- Mocked UI E2E specs, such as `text-length.spec.ts` and `tts-workbench.spec.ts`, mock selected backend routes to keep UI behavior deterministic.
- Real deployed frontend-backend E2E specs, such as `deployed-real-backend.spec.ts`, must not mock the backend route they verify and should use stable internal endpoints without external provider dependencies.

To run Playwright against an already deployed environment instead of local web servers:

```bash
cd frontend
E2E_BASE_URL="https://<deployed-host>" E2E_USE_LOCAL_SERVERS=false npm run test:e2e
```

### Mandatory CI/CD pipeline checks

The GitHub Actions deployment pipeline must run E2E tests in a separate `predeploy-e2e` job before the real Hetzner deployment. Pipeline E2E tests are mandatory even though local/Codex E2E execution is optional. Full E2E must not run against production/Hetzner.

The pipeline must:

1. Build backend and frontend images.
2. Deploy the same Helm chart with those images into an isolated temporary CI environment, preferably kind or k3d.
3. Use CI-safe configuration only, with no production secrets, no production data, and no real Hetzner connection.
4. Wait for Helm/Kubernetes rollout success and for backend/frontend pods to be Ready using Kubernetes readiness checks.
5. Run all E2E tests against the temporary CI URL, including the real frontend-backend spec.
6. Fail if readiness fails, the real backend integration check fails, or any E2E test fails.
7. Skip the real `deploy` job if `predeploy-e2e` fails.

### Frontend test rule

Angular/Karma tests must run successfully.

In Codex Web, run frontend tests with:

```bash
cd frontend
CHROME_BIN="${CHROME_BIN:-/tmp/chrome-no-sandbox}" npm test
```

Do not append duplicate `--watch=false --browsers=ChromeHeadless` flags, because they are already defined in `frontend/package.json`.

## Codex Web validation rule

Do not run Docker commands in Codex Web.
Skip all Docker-based validation.

Use these non-Docker validation commands where applicable:
- Backend: `cd backend && gradle build`
- Frontend unit tests: `cd frontend && CHROME_BIN="${CHROME_BIN:-/tmp/chrome-no-sandbox}" npm test`
- Frontend build: `cd frontend && npm run build`

## Error handling and observability

Silent exception swallowing is forbidden in production code.

Do not use:

```java
catch (Exception ignored) {
}
```

Backend API failures must be propagated to callers through the global structured error response shape. Logs should keep technical details and exception causes; frontend responses must stay safe and must not expose stack traces, secrets, SQL, credentials, tokens, or internal hostnames.
