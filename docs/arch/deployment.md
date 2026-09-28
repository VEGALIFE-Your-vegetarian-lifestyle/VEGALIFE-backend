# Deployment

How Vegalife is deployed to Render, how CD is triggered, and what to verify
after a deploy. Issue: [#64](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/64).

## Environments

| Environment | Render service | URL | Database | Trigger |
|---|---|---|---|---|
| Local dev | Docker Compose | `http://localhost:8080` | Dockerized PostgreSQL | `./mvnw spring-boot:run` (profile `dev`) |
| Staging | `staging-vegalife` | <https://staging-vegalife.onrender.com> | Neon branch `staging` | Push to `main`, or manual dispatch with `environment=staging` |
| Production | `api-vegalife` | <https://api-vegalife.onrender.com> | Neon branch `main` | Tag `v*`, or manual dispatch with `environment=production` |

Both Render services run on the **free plan** with `SPRING_PROFILES_ACTIVE=prod`
(staging and prod differ only by their secret values — Neon branch and
Brevo credentials). Free instances sleep after ~15 minutes of no traffic;
a cold start can take up to ~1 minute. Free workspaces get **750
instance-hours per month**, shared across both services — enough for both
services combined (~375 h each), with no keep-alive pings. Render free
**blocks outbound ports 25, 465, and 587** (announced 2025-09-26), which
drives the SMTP port choice below.

## Architecture

- **Render builds from the repo's `Dockerfile`** (blueprint: `render.yaml`,
  `dockerPort: 8080`). The CD workflow does not push to Render — it builds
  and pushes the GHCR image as a verification gate, then fires the Render
  **deploy hook**. No Render registry credentials are needed.
- **Health checks**: `render.yaml` sets `healthCheckPath: /actuator/health`;
  `Dockerfile` `HEALTHCHECK` hits the same path. Only the health endpoint is
  exposed (`management.endpoints.web.exposure.include: health`,
  `show-details: never`) and it is `permitAll()` in `SecurityConfig` — it
  returns `{"status":"UP"}` with no details.
- **Database**: external Neon PostgreSQL (pooled connection string,
  `sslmode=require`). Each environment uses its own Neon branch;
   `render.yaml` has no `databases:` block. Flyway owns the schema
   (`ddl-auto: none` in prod — never Hibernate auto-DDL in production).
- **Deploy hook `?ref=<sha>`**: Render deploy hooks accept a `ref` query
  parameter naming a commit SHA to build, so a manual dispatch from `dev`
  deploys that exact commit instead of the service's configured branch HEAD.

## CD triggers (`.github/workflows/cd.yml`)

| Trigger | Runs |
|---|---|
| Push to `dev` | **Nothing.** CD never fires on plain `dev` pushes. |
| Push to `main` | `docker-build` → `deploy-staging` (deploy hook → staging) |
| Push of tag `v*` | `docker-build` → `deploy-production` (deploy hook → prod, gated by the GitHub `production` environment) |
| `workflow_dispatch` (Actions tab → CD → Run workflow, choose branch + environment) | `docker-build` → chosen deploy job; deploy hook called with `?ref=$GITHUB_SHA` of the dispatched branch |

Manual dev deploy: **Actions → CD → Run workflow → branch `dev` → environment
`staging`**. Environments `staging` and `production` exist in the repo
settings; add required reviewers to `production` to gate every prod deploy
(manual and tag-triggered alike).

## Secrets

Never commit secrets. Values are pasted by hand into:

| Where | Keys |
|---|---|
| GitHub repo secrets (Settings → Secrets and variables → Actions) | `RENDER_STAGING_DEPLOY_HOOK`, `RENDER_PRODUCTION_DEPLOY_HOOK` (the hook URLs from each Render service's *Deploys* page) |
| GitHub environments `staging` / `production` | Optional reviewer gating for `production` |
| Render dashboard (per service; `render.yaml` marks them `sync: false`) | `DB_URL`, `DB_USER`, `DB_PASSWORD`, `AUTH_JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` |

## Environment variables

Base defaults live in `application.yml`; dev-profile overrides in
`application-dev.yml`; prod-only overrides and fail-fast requireds in
`application-prod.yml`.

| Variable | Local (`.env.example`) | Prod default | Notes |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` (set in `render.yaml`) | |
| `SERVER_PORT` / `PORT` | `SERVER_PORT=8080` | `${PORT:${SERVER_PORT:8080}}` | Render injects `PORT` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Docker Compose defaults | **required, no default** | Neon pooled URL, `sslmode=require` |
| `AUTH_JWT_SECRET` | dev placeholder | **required, no default** | ≥ 32 chars |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` (dev profile) | `smtp-relay.brevo.com` / `2525` | Same keys in every profile; dev default is localhost:1025 (MailHog/mailpit); **port 2525** in prod because Render blocks 25/465/587 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | empty | **required, no default** | Brevo SMTP key |
| `MAIL_FROM` | `your-verified-sender@example.com` | **required, no default** | Must be a sender your SMTP relay accepts (e.g. Brevo-verified) |
| `app.mail.from` → `MAIL_FROM` | default in `application.yml` | required in prod | Used by `SmtpEmailServiceImpl` `setFrom` |

Missing required prod vars make the app **fail fast at startup** (unresolvable
placeholder) — Render shows the deploy as failed with the placeholder name in
the log, instead of failing later on first use.

### SMTP port caveat (verify once)

Brevo officially supports 2525, and 2525 is open from this repo's local
network, but **Render's allowlist must be confirmed on the running instance**
(deploy once, then check whether password-reset mail delivers). If 2525 is
also blocked, switch `MAIL_HOST` to the Brevo HTTP API via
`BrevoApiEmailServiceImpl` (HTTPS/443) — no code change to the SMTP service
itself is required for the check.

## Verification gates

| Gate | Command / action | Needs Phase 2? |
|---|---|---|
| G1 Full build | `./mvnw clean verify` | no |
| G2 Prod smoke (local) | Run with `SPRING_PROFILES_ACTIVE=prod` against an empty Docker Postgres; Flyway V1→V16, `GET /actuator/health` → 200 `UP` | no |
| G3 PR CI | PR checks green | no |
| G4 Staging health | `curl https://staging-vegalife.onrender.com/actuator/health` | yes |
| G5 Email delivery | Request a password reset; OTP arrives from `MAIL_FROM` | yes |
| G6 Production | Push tag `v*` → `production` environment approval → deploy | yes |
| G7 Rollback drill | Re-run CD on the previous green tag/sha (deploy hook `?ref=`) | yes |

## Rollback

1. Staging: re-dispatch CD on the last known-good `main` SHA (or push a
   revert commit to `main`).
2. Production: create/deploy the previous release tag (`git tag v X.Y.Z && git push origin vX.Y.Z`) — CD redeploys that SHA via the deploy hook.
3. Database: Flyway migrations are forward-only; roll back application code
   only. A bad migration needs a new forward-fix migration, not a revert.

## Local parity

```bash
docker compose up -d        # postgres + mailhog
./mvnw spring-boot:run      # dev profile, port 8080, SMTP localhost:1025
```

`GET http://localhost:8080/actuator/health` must return `{"status":"UP"}`.
