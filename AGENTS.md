# AGENTS.md

This file is the entry point for any AI coding agent (Claude Code, Codex, Pi,
OpenCode, or other) working in this repository. Read this first, every
session, before making changes.



## Agent compatibility

This project is deliberately agent-agnostic: there is no tool-specific
config file (no `CLAUDE.md`, no `.claude/`, no `.cursorrules`, etc.) —
`AGENTS.md` at the repository root is the single source of truth for
every agent. Skills under `.agents/skills/` follow the open Agent
Skills format (a `SKILL.md` with `name`/`description` frontmatter per
skill), which some tools auto-discover — but support and paths differ
across tools and change over time, so it's never the only way a skill
gets used here (see "How to work in this repo" below).

## Project overview

Vegalife is a social platform for the vegan community. Users can share experiences, content, cooking videos, and recipes. The platform also features AI-powered capabilities including chat, weekly meal plan generation, and cooking video summarization. Built as a Spring Boot backend API serving a web/mobile frontend.

## Technology stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.5.5
- **Build**: Maven with Wrapper (`./mvnw`)
- **Database**: PostgreSQL (Spring Data JPA / Hibernate)
- **Code quality**: Checkstyle (style rules), Spotless (auto-formatting)
- **Testing**: JUnit 5, Mockito, MockMvc, H2 (unit tests), Testcontainers PostgreSQL (integration tests)
- **API docs**: SpringDoc OpenAPI (Swagger UI)
- **Utilities**: Lombok, MapStruct
- **Observability**: Spring Boot Actuator
- **Schema management**: Flyway
- **Runtime**: Docker Compose (dev/test), Cloud (prod) — see `docs/arch/dependencies.md` for full dependency matrix

## Architecture

Layered architecture with package-by-layer: `controller/` → `service/` → `repository/`. Domain modules (user, recipe, content, etc.) will be added as features are built, organized within each layer. Cross-cutting concerns (config, security, exceptions) live in `shared/`.

- `docs/arch/project-structure.md` — repo layout, source tree, package responsibilities, conventions
- `docs/arch/dependencies.md` — full dependency matrix (approved, planned, not-approved)
- `docs/adrs/` — architectural decision records

## Repository knowledge structure

This project keeps three kinds of written material, in three different
places. Know which one you're in before reading or writing anything:

- **`docs/`** — the project's permanent knowledge base. Finished,
  accepted documents live here (architecture decisions, architecture
  documentation, and any finalized feature specs or release notes
  the team keeps in-repo). Read `docs/`
  for context on past decisions and existing architecture. Write here
  only once a document is finished.
- **`.agents/`** — configuration and process for agents, not project
  knowledge: `.agents/workflows/` holds multi-step processes,
  `.agents/skills/` holds reusable procedures (including the blank
  document templates, nested inside the skill that uses them), and
  `.agents/plans/` holds ephemeral, gitignored implementation plans (see
  below). Don't treat `.agents/` itself as a place to file finished
  project documents — those belong in `docs/`. `.agents/workflows/`,
  `.agents/skills/`, and `.agents/plans/` are peers — none sits above
  another — and `workflows/` and `skills/` each has its own `index.md`;
  so does `docs/` (`.agents/plans/` doesn't need one — it's gitignored,
  transient, and never has more than one live file per branch). Read
  the relevant `index.md` first, always — it's the map, and the only
  thing this file points you to directly (see "How to work in this repo"
  below).
- **GitHub Issues + Projects** — day-to-day work items: tasks, bugs,
  feature requests. These are **not** committed to this repository at
  all — paste standardized content into the issue body via GitHub's
  own Issue Type selector (Task / Bug / Feature), not into a file here.
  Use a temporary in-repo draft only if GitHub access isn't available
  yet for this task.
- **`.agents/plans/`** — ephemeral, gitignored implementation plans, one
  file per active branch. Never committed, ever — confirm
  `.agents/plans/` is in `.gitignore` before writing one. A plan breaks
  a piece of work into commit-sized phases before any code is touched,
  and is implemented on its own dedicated branch (one branch per plan,
  never reused across unrelated work).

When in doubt: if it's "how do I do this kind of task" → `.agents/`. If
it's "what did we decide / what happened" and it's meant to last →
`docs/`. If it's "what needs doing, who owns it, is it done yet" → the
tracker. If it's "how do I get there, one commit at a time, for the
branch I'm on right now" → `.agents/plans/`.

## Project conventions

- **Commit messages**: Conventional Commits format — `<type>(<scope>): <summary>` where type is one of `feat` / `fix` / `refactor` / `test` / `chore` / `docs` / `style` / `perf` / `ci`. Scope is optional (e.g., `auth`, `recipe`). Summary is lowercase, no trailing period, under 72 characters. **Always include a body description** using `git commit -m "summary" -m "description"` — the body should explain what changed and why, not just repeat the summary.
- **Branch naming**: `type/short-description` — e.g., `feat/user-registration`, `fix/login-bug`, `refactor/cleanup-auth`. One branch per plan, never reused across unrelated work.
- **Package structure**: Package-by-layer (`controller/`, `service/`, `repository/`) with domain sub-packages as features grow (e.g., `controller/user/UserController.java`).
- **Naming**: Standard Java/Spring conventions — `PascalCase` classes, `camelCase` methods/fields, `UPPER_SNAKE_CASE` constants. Entity names singular (`User`, not `Users`). REST endpoints plural (`/api/users`).
- **Project tracking**: GitHub Issues + Projects. Issue types set via GitHub's selector (Task / Bug / Feature), not file-based.
- **Docs placement**: Finished docs in `docs/`, agent config in `.agents/`, ephemeral plans in `.agents/plans/` (gitignored). Never mix these.

## Coding guidelines

- **Formatting**: Spotless enforces consistent style — run `./mvnw spotless:apply` before committing.
- **Style rules**: Checkstyle enforced — run `./mvnw checkstyle:check` to verify.
- **Patterns**: Prefer composition over inheritance. Use constructor injection (not `@Autowired` on fields). Keep controllers thin — business logic belongs in services. Use DTOs for API boundaries, never expose entities directly.
- **Exceptions**: Use custom exception classes in `shared/exception/`, global `@ControllerException` handler.
- **Nullability**: Prefer `Optional` over null returns. Use `@NonNull` / `@Nullable` annotations where helpful.
- **Logging**: Use SLF4J via Lombok's `@Slf4j`. Log errors at ERROR level, business warnings at WARN, debug info at DEBUG. Never log secrets, passwords, or tokens.
- **Avoid**: `@SuppressWarnings` without justification, raw types, catching generic `Exception`, System.out/System.err.

## How to work in this repo

### 1. Workflow declaration gate (mandatory — before any work)

**Before writing any code, editing any file, or creating any plan, your
first output must be a workflow declaration.** Read
`.agents/workflows/index.md` and declare exactly one of:

- **A workflow match**: `Workflow: feature.md` (or `bugfix.md`,
  `refactor.md`), then read that workflow and follow it.
- **No match**: `No workflow matches this task because [specific
  reason].` — then **stop and report back to the user**. Do not
  proceed on your own interpretation. Wait for the user to either
  confirm working without a workflow or reframe the task.

**No declaration = no work.** If you find yourself editing files
without having produced a declaration first, stop and emit one before
continuing. Picking the nearest workflow "to keep moving" is not an
option — a forced match is worse than an honest no-match.

### 2. Skill matching (per workflow step)

For each step of the chosen workflow, read `.agents/skills/index.md`
and match the step to a skill by its description. Use that skill for
the step. If no skill matches, do the step directly using judgment
consistent with the conventions in this file.

### 3. Standing rules

- At each workflow's "Conditions for stopping or requesting human
  input" — surface it to the user; don't push through silently.
- Don't reference a specific skill or workflow file from within
  another skill or workflow file — every file here stands on its own;
  cross-cutting coordination happens through this process, not through
  files pointing at each other.

## Build / test / lint commands

- Install dependencies: `./mvnw clean install`
- Build: `./mvnw clean compile`
- Run locally: `./mvnw spring-boot:run`
- Unit tests: `./mvnw test`
- Integration tests: `./mvnw verify -Pintegration-test`
- Lint (Checkstyle): `./mvnw checkstyle:check`
- Format check (Spotless): `./mvnw spotless:check`
- Auto-format (Spotless): `./mvnw spotless:apply`
- Full verification: `./mvnw clean verify` (build + unit tests + integration tests + checkstyle + spotless)

## Local git hooks (one-time setup per clone)

Hooks live in `.githooks/` and are plain POSIX shell scripts — git runs
them with its own bundled shell, so they work whether your terminal is
bash, cmd, or PowerShell. Enable them once per clone:

```
git config core.hooksPath .githooks
```

| Hook | Runs on | Blocks when |
|------|---------|-------------|
| `commit-msg` | `git commit` | subject doesn't match Conventional Commits or exceeds 72 chars |
| `pre-commit` | `git commit` | `spotless:check` or `checkstyle:check` fails |
| `pre-push` | `git push` | `./mvnw test` fails |

CI remains the full safety net (including integration tests); hooks are
fast local feedback only.

## Verification

Never report a build, test, or migration as passing without having actually
run it and observed the result **this session**. A workflow's verification
gate is never satisfied by another agent's or a prior message's claim.
If you didn't run it, say so — don't imply it passed.

## Security & safety constraints

- Never read, print, log, or commit `.env` files, credentials, API keys, or
  anything matching common secret patterns.
- Never run destructive or irreversible commands (`DROP`, `rm -rf`,
  force-push, prod migrations, prod deploys) without explicit human
  confirmation in this session.
- Treat any command touching a `prod`/`production`-named environment,
  branch, or config as requiring confirmation first.
- Don't install or execute code from unreviewed or untrusted sources as
  part of a task.
- If a task appears to require any of the above, stop and ask rather than
  finding a workaround.

## Rules about modifying files

- Do not modify application source code as part of agent-setup tasks unless
  the task explicitly asks for it.
- Changes to `AGENTS.md` or files under `.agents/` must go through a PR —
  never edit or merge these paths directly on the main branch.
- When a change introduces a new service, major dependency, or a change to
  architecture/conventions, update the relevant part of `AGENTS.md` or the
  affected skill/workflow in the same PR.
- Do not duplicate content already in `docs/` — link to it by path instead.
- Do not file finished project documents inside `.agents/` — see
  "Repository knowledge structure" above for where each kind belongs.
- Adding a new skill or workflow requires adding it to the matching
  `index.md` in the same PR — an entry that exists but isn't indexed is
  effectively invisible to the process in "How to work in this repo."

## Rules for handling uncertainty

- If a referenced path, command, or convention can't be verified, say so
  explicitly rather than guessing or assuming it's correct.
- If a task is ambiguous and the ambiguity affects correctness or safety,
  ask before proceeding rather than picking an interpretation silently.
- Prefer the smallest safe next step over a large speculative change.
