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

Automated checks are mandatory. A change is not done if tests cannot run.

### Required quality gates

Before a change is considered complete, the following checks must pass where applicable:

1. Frontend unit tests
2. Backend unit tests
3. Application build
4. Docker image build
5. Helm chart validation, if chart files were changed
6. README update, if behavior, setup, deployment, or quality checks changed

### Frontend test rule

Angular/Karma tests must run successfully

## Codex Web validation rule

Do not run Docker commands in Codex Web.
Skip all Docker-based validation.
