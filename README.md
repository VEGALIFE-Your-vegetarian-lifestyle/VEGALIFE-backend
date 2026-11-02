# VEGALIFE-backend

A Spring Boot backend API for **Vegalife** — a social platform for the vegan community. Users can share experiences, cooking videos, recipes, and connect with others. The platform includes AI-powered features: chat assistant, weekly meal plan generation, and cooking video summarization.

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.5 |
| Build | Maven Wrapper (`./mvnw`) |
| Database | PostgreSQL (Spring Data JPA / Hibernate) |
| Schema Migration | Flyway |
| API Documentation | SpringDoc OpenAPI (Swagger UI) |
| Code Quality | Checkstyle, Spotless |
| Testing | JUnit 5, Mockito, MockMvc, H2 |
| Utilities | Lombok, MapStruct |
| Observability | SLF4J logs (Spring Boot Actuator planned) |
| Runtime | Docker Compose (dev/test), Cloud (prod) |

---

## Architecture

**Layered architecture** (package-by-layer):

```
controller/ → service/ → repository/
```

- Domain modules (user, recipe, content, etc.) organized within each layer
- Cross-cutting concerns (config, security, exceptions) in `shared/`
- See [`docs/arch/project-structure.md`](docs/arch/project-structure.md) for full source tree and conventions
- See [`docs/arch/dependencies.md`](docs/arch/dependencies.md) for approved/planned/not-approved dependency matrix

---

## Documentation

All permanent project knowledge lives in [`docs/`](docs/):

| Directory | Purpose |
|-----------|---------|
| [`docs/arch/`](docs/arch/) | Architecture docs — project structure, dependency matrix |
| [`docs/adrs/`](docs/adrs/) | Architecture Decision Records (ADRs) |
| [`docs/apis/`](docs/apis/) | Supplementary API reference docs (complements Swagger UI) |
| [`docs/brs/`](docs/brs/) | Business rules — constraints independent of features |
| [`docs/feats/`](docs/feats/) | Feature specifications with design, metrics, acceptance criteria |
| [`docs/releases/`](docs/releases/) | Release notes (created per version) |

Agent configuration and ephemeral plans are in [`.agents/`](.agents/) — not project documentation.

---

## Quick Start

### Prerequisites
- Java 21+
- PostgreSQL (or use Docker Compose)
- Maven (or use `./mvnw`)

### Local Development

```bash
# Start PostgreSQL + app via Docker Compose
docker compose up -d

# Or run locally with your own Postgres
./mvnw spring-boot:run
```

### Build & Test

```bash
# Full verification (build + test + checkstyle + spotless)
./mvnw clean verify

# Individual commands
./mvnw clean compile       # Build
./mvnw test                # Run tests
./mvnw checkstyle:check    # Style check
./mvnw spotless:check      # Format check
./mvnw spotless:apply      # Auto-format
```

### API Documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

---

## Project Conventions

- **Commits**: Conventional Commits — `<type>(<scope>): <description>`
  - Types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `style`, `perf`, `ci`
- **Branches**: `type/short-description` (e.g., `feat/user-registration`, `fix/login-bug`)
- **Packages**: `controller/`, `service/`, `repository/` with domain sub-packages
- **Naming**: Standard Java/Spring — `PascalCase` classes, `camelCase` methods, `UPPER_SNAKE_CASE` constants
- **REST endpoints**: Plural (`/api/users`, `/api/recipes`)
- **Entities**: Singular (`User`, `Recipe`)
- **DTOs at API boundaries** — never expose entities directly
- **Constructor injection** via Lombok `@RequiredArgsConstructor`

---

## Working in This Repo

### For AI Agents
Start with [`AGENTS.md`](AGENTS.md) — the single source of truth for agent workflows, skills, and conventions.

### For Humans
1. Check [`docs/arch/project-structure.md`](docs/arch/project-structure.md) for repo layout
2. Read [`docs/arch/dependencies.md`](docs/arch/dependencies.md) before adding dependencies
3. Check [`docs/adrs/`](docs/adrs/) for existing architectural decisions
4. New features → write a spec in [`docs/feats/`](docs/feats/) using the feature-spec template
5. Business rules → add to [`docs/brs/`](docs/brs/)
6. API docs → add to [`docs/apis/`](docs/apis/)

### Tracking
- Day-to-day work: GitHub Issues + Projects (Task / Bug / Feature types)
- Not committed to repo — use GitHub's issue type selector

---

## License

[Add your license here]