---
name: deployment
description: Use whenever setting up or modifying Docker Compose, cloud deployment, CI/CD pipelines, environment configuration, or production infrastructure. References docs/ for provider-specific details. Focuses on the deployment process and verification, not provider-specific implementation.
---

# Skill: Deployment

## When to use this skill

Use whenever the task touches deployment, infrastructure, Docker, CI/CD,
or environment configuration -- setting up Docker Compose for local dev,
configuring cloud services, writing deployment scripts, or troubleshooting
deployment issues.

## Required context / input

- The deployment task at hand.
- `docs/arch/dependencies.md` -- check before adding infrastructure
  dependencies (e.g., Redis client, cloud SDK).
- `docs/` -- provider-specific infrastructure documentation (which cloud,
  which services, credentials setup).
- `AGENTS.md` -- security constraints (never commit secrets, confirm prod
  operations).

## Procedure

### 1. Local development (Docker Compose)

- Place `docker-compose.yml` at the project root (or `docker-compose.dev.yml`
  for dev-specific config).
- Services to include:
  - `postgres` -- PostgreSQL database with persistent volume.
  - (Add `redis`, `minio`, etc. as needed -- but only when a feature
    actually requires them.)
- Use `environment` or `.env` files for configuration -- never hardcode
  credentials in `docker-compose.yml`.
- Expose ports explicitly (e.g., `5432:5432` for Postgres, `8080:8080`
  for the app).
- Use health checks (`healthcheck` section) so dependent services wait
  for readiness.
- Verify: `docker compose up -d` starts all services, app connects to
  the database, `docker compose down` cleans up.

### 2. Profiles

- Spring Boot profiles control config per environment:
  - `dev` -- local development (Docker Compose services, relaxed logging).
  - `test` -- integration tests (H2 or testcontainers, not the dev DB).
  - `prod` -- production (cloud services, strict security, structured logging).
- Profile-specific properties: `application-dev.properties`,
  `application-test.properties`, `application-prod.properties`.
- Never put production secrets in `application.properties` -- use environment
  variables or a secrets manager.

### 3. Cloud deployment

- **Always reference `docs/` for provider-specific details.** This skill
  defines the process, not the provider.
- Deployment steps vary by provider but always follow this order:
  1. Ensure all tests pass locally (`./mvnw clean verify`).
  2. Build production artifact: `./mvnw clean package -DskipTests=false`.
  3. Push to container registry or deploy artifact per provider docs.
  4. Run health check against deployed endpoint.
  5. Verify logs show no errors.
- Document the actual deployment process in `docs/arch/` once
  it's working -- future agents and team members need this.

### 4. CI/CD

- If setting up CI/CD (GitHub Actions, GitLab CI, etc.), place config
  in the standard location (`.github/workflows/`, `.gitlab-ci.yml`).
- CI pipeline should at minimum: build, run tests, run checkstyle, run
  spotless check.
- CD pipeline (if separate): build artifact, deploy to staging, run
  smoke tests, promote to production with approval gate.
- Never store secrets in CI config files -- use the platform's secret
  management (GitHub Secrets, GitLab Variables, etc.).

### 5. Environment variables and secrets

- Use `${ENV_VAR:default}` syntax in `application.properties`.
- Required environment variables for production:
  - `DB_URL`, `DB_USER`, `DB_PASSWORD` -- database connection.
  - `SPRING_PROFILES_ACTIVE=prod` -- activate production profile.
  - Any API keys or external service credentials.
- **Never** commit `.env` files, credentials, API keys, or anything
  matching secret patterns to the repository.
- For local dev: use `.env` file (gitignored) with Docker Compose.

## Relevant project conventions

- Docker Compose for dev/test, cloud for production.
- Provider-specific details live in `docs/`, not in this skill.
- Secrets are never committed -- environment variables or secrets managers only.

## Verification steps

- `docker compose up -d` starts all services without errors.
- App connects to the database (check logs for successful startup).
- Health endpoint responds: `curl http://localhost:8080/actuator/health`.
- `docker compose down` cleans up cleanly.
- No secrets appear in committed files (check `git status`).
- For cloud: deployed endpoint responds, logs show no errors.

## Expected artifacts / output

- `docker-compose.yml` (or `docker-compose.dev.yml`) if setting up local dev.
- Environment configuration files (gitignored `.env` for local).
- CI/CD pipeline config if setting up automation.
- Documentation in `docs/arch/` describing the deployment setup.
