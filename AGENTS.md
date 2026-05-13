# AGENTS.md

## Main engineering priorities

The three most important engineering priorities are:

1. Single responsibility: each component, class, function, workflow, and configuration file should have one clear purpose.
2. High test coverage: important behavior must be protected by meaningful automated tests, preferably written or adjusted before the implementation.
3. Easy maintenance: solutions should stay simple, understandable, easy to review, and easy to change later.

When these priorities conflict, prefer the solution that keeps the codebase easier to understand and safer to change.

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
- Prefer existing project capabilities over custom code: before writing new styling, utility logic, configuration, or infrastructure, check whether the project already has a simple established way to solve the task.
- Do not bypass supported framework tooling with a hand-written workaround when the project already has a standard generator, integration, or extension point for the problem.

## Frontend-specific rules

- Structure frontend changes component-first: split UI into small, meaningful Angular components with clear responsibilities instead of growing large page-level templates.
- When frontend behavior is changed, run the frontend unit tests, frontend build, and end-to-end tests.
- Do not treat a frontend change as done until E2E tests have passed, unless the environment technically cannot run them. If they cannot be run, clearly state why.
- Avoid expensive, mutating, regex-heavy, or non-memoized methods in templates/render paths.
- Derived UI state should be precomputed, memoized, signal-based, selector-based, or otherwise cheap and pure.
- New UI components should be small, focused, and compatible with the project’s preferred change-detection/rendering strategy.
- Prefer stable test selectors such as `data-testid` over brittle tests based only on visible text, CSS classes, or DOM position.


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
7. Contract verification when API shapes or shared fixtures change:
   - `cd frontend && npm run verify:api-contract`
   - backend controller tests that validate responses against `shared/api-contract/tts-lab-openapi.yaml`
   - shared fixture tests that read from `test-contracts/`

### End-to-end tests in Codex/local workflow

End-to-end tests are optional for Codex/local agent execution. Do not treat E2E as a required local quality gate for every change, because they start the full application stack and are slower than unit/build checks.

Run E2E manually when a change affects cross-service behavior, routing, authentication flow, deployment-only behavior, or when the user explicitly asks for it:

```bash
cd frontend
npm run test:e2e
```

The Playwright suite contains two kinds of tests:

- Mocked UI E2E specs, such as `text-length.spec.ts` and `tts-workbench.spec.ts`, mock selected backend routes to keep UI behavior deterministic.
- Real frontend-backend E2E specs, such as `real-backend-health.spec.ts`, must not mock the backend route they verify and should use stable internal endpoints without external provider dependencies.

Contract testing applies alongside E2E for API-surface changes:

- Keep `shared/api-contract/tts-lab-openapi.yaml` as the schema source of truth.
- Keep `test-contracts/` as the shared request/response fixture source of truth for frontend and backend tests.
- Do not reintroduce handwritten schema mirrors in the frontend; use generated types and `npm run verify:api-contract` instead.

To run Playwright against an already deployed environment instead of local web servers:

```bash
cd frontend
E2E_BASE_URL="https://<deployed-host>" E2E_USE_LOCAL_SERVERS=false npm run test:e2e
```

## Reuse existing project capabilities

Before implementing a custom solution, inspect the existing project setup, dependencies, configuration, and coding conventions.

Prefer using capabilities that are already available in the project when they make the change smaller, clearer, and easier to maintain.

Do not write large amounts of custom styling, utility logic, configuration, or infrastructure code before checking whether the project already provides a simpler established way to solve the same problem.

Only introduce a new dependency or approach when it clearly reduces complexity, fits the learning-oriented scope of the project, and can be explained briefly in the task summary.

### Mandatory CI/CD pipeline checks

The GitHub Actions pipeline must run E2E tests locally inside the GitHub Actions runner. Pipeline E2E tests are mandatory even though local/Codex E2E execution is optional.

The pipeline must:

1. Deploy backend and frontend in the deployment job.
2. Wait for Helm/Kubernetes rollout success in the deployment job.
3. Wait for backend and frontend pods to be Ready using Kubernetes readiness checks in the deployment job.
4. Run all E2E tests in a separate local runner job with Playwright's webServer configuration enabled (`E2E_USE_LOCAL_SERVERS=true` or omitted) and `E2E_BASE_URL=http://127.0.0.1:4200`.
5. Fail if readiness fails, the real local frontend-backend integration check fails, or any E2E test fails.

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


## Codex app validation rule

The Codex app may run inside Docker Desktop's internal Linux environment. This is visible from prompts like:

```bash
docker-desktop:/tmp/docker-desktop-root/run/desktop/mnt/host/c/Users/Chris/Documents/Codex/tts-lab#
```

In that environment, Windows-installed Java and Gradle are not available through `PATH`. If `java` or `gradle` returns `not found`, do not install Java or Gradle into the Docker Desktop internal shell.

For backend validation in the Codex app, use a Docker image that already contains Java 21 and Gradle 8.14:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace gradle:8.14-jdk21 gradle clean test
```

If the project has a Gradle wrapper, prefer the wrapper inside the same container:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace gradle:8.14-jdk21 ./gradlew clean test
```

For a full backend build, use:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace gradle:8.14-jdk21 gradle build
```

Only run `java`, `gradle`, or `./gradlew` directly when the Codex app was started from an environment where those commands are already available, for example Windows PowerShell with a correctly configured `PATH`.

## Error handling and observability

Silent exception swallowing is forbidden in production code.

Do not use:

```java
catch (Exception ignored) {
}
```

Backend API failures must be propagated to callers through the global structured error response shape. Logs should keep technical details and exception causes; frontend responses must stay safe and must not expose stack traces, secrets, SQL, credentials, tokens, or internal hostnames.
