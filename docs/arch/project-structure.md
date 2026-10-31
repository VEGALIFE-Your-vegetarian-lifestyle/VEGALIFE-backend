# Project Structure

## Repo root layout

```
VEGALIFE-backend/
├── .git/
├── .gitignore
├── .agents/                # Agent config (skills, workflows, plans)
│   ├── skills/             #   Reusable procedures + reference templates
│   ├── workflows/          #   Multi-step processes
│   └── plans/              #   Ephemeral, gitignored implementation plans
├── docs/                   # Permanent project knowledge base
│   ├── arch/               #   Project structure, dependency matrix
│   ├── adrs/               #   Architecture decision records
│   ├── apis/               #   API reference docs
│   ├── brs/                #   Business constraints
│   └── feats/              #   Feature specifications
├── src/                    # The Spring Boot API application
│   ├── main/
│   │   ├── java/           # Application source code
│   │   └── resources/      # Config: application.yml, Flyway migrations
│   └── test/
│       ├── java/           # Tests mirroring src/main structure
│       └── resources/      # Test config + Testcontainers support files
├── AGENTS.md               # Entry point for AI agents
├── Dockerfile              # API image (build context = repo root)
├── pom.xml                 # Maven build — single module (dependencies + plugins)
├── docker-compose.yml      # Local dev stack (postgres + API)
└── README.md               # Human-facing project README
```

One Maven module, one `src/` at the root. The AI chat's menu tools are
`@Tool` methods in `service/ai/` inside this same application, not a separate
deployable — see `docs/adrs/013-serve-ai-tools-in-process.md`.

## Source tree

Packages are ranked: a package may import from its own rank or a lower one, never a higher one.
The ranking and the reasoning behind it are in `docs/adrs/018-clean-architecture-package-dependencies.md`;
`tools/rank-check.ps1` fails the build when an upward edge appears.

```
src/main/java/com/vegalife/
├── VegalifeApplication.java          # Spring Boot entry point
│
├── shared/                           # (1) Kernel — depends on nothing in this app
│   ├── dto/                          #   Cross-layer response envelopes (ApiResponse, PageResponse)
│   ├── exception/                    #   Custom exceptions + GlobalExceptionHandler
│   ├── openapi/                      #   OpenAPI scheme name constants (OpenApiSchemeNames)
│   └── validation/                   #   Reusable constraint annotations + validators
│
├── enums/                            # (1) Enumerations, grouped by domain
│   ├── <domain>/                     #   e.g. user/UserRole.java, post/PostStatus.java
│   └── ...
│
├── model/                            # (2) JPA entities only — no enums, no DTOs
│   ├── <domain>/                     #   e.g. user/User.java, recipe/Recipe.java
│   └── ...
│
├── repository/                       # (3) Data access (Spring Data JPA) + Specifications
│   ├── <domain>/                     #   e.g. user/UserRepository.java
│   └── ...
│
├── dto/                              # (4) API contracts + entity↔DTO mappers
│   ├── request/                      #   Inbound DTOs
│   │   └── <domain>/<Name>Request.java
│   ├── response/                     #   Outbound DTOs
│   │   └── <domain>/<Name>Response.java
│   └── mapper/                       #   MapStruct/hand-written mappers
│       └── <domain>/
│
├── service/                          # (5) Business logic, transactions, ports
│   ├── <domain>/                     #   e.g. user/, recipe/, filter/
│   │   ├── <Domain>Service.java
│   │   └── *Properties.java          #     @ConfigurationProperties owned by the consumer
│   └── ...                           #   Ports (OutboundChannelAdapter, EmailService,
│                                     #   GoogleIdentityProvider, PaymentGateway) live here
│
├── infrastructure/                   # (6) Adapters to the outside world
│   ├── email/                        #   SMTP, outbound channel adapter
│   ├── embedding/                    #   Gemini/HF embedding clients
│   ├── google/                       #   Google OAuth exchange + ID-token verification
│   ├── payment/vnpay/                #   VNPay client + signer
│   └── security/                     #   JWT authentication filter/provider/token
│
├── controller/                       # (7) REST API layer — thin, no business logic
│   └── <domain>/<Domain>Controller.java
├── scheduled/                        # (7) @Scheduled jobs
│
└── config/                           # (8) Composition root — Spring @Configuration only
    └── *Config.java                  #   SecurityConfig, OpenApiConfig, CacheConfig, ...
```

## Test tree

```
src/test/java/com/vegalife/
├── VegalifeApplicationTest.java          # Context load test (unit)
│
├── unit/                                 # mvn test (Surefire, H2)
│   ├── controller/                       # @WebMvcTest + MockMvc tests
│   │   └── <domain>/
│   │       └── <Domain>ControllerTest.java
│   │
│   ├── service/                          # Unit tests with Mockito
│   │   └── <domain>/
│   │       └── <Domain>ServiceTest.java
│   │
│   └── repository/                       # @DataJpaTest + H2 in-memory DB
│       └── <domain>/
│           └── <Domain>RepositoryTest.java
│
└── integration/                          # mvn verify -Pintegration-test (Failsafe, Testcontainers PostgreSQL)
    ├── config/                           # Testcontainers config + base classes
    ├── controller/                       # @SpringBootTest + MockMvc
    │   └── <domain>/
    │       └── <Domain>ControllerIntegrationTest.java
    │
    └── service/                          # @SpringBootTest + real DB
        └── <domain>/
            └── <Domain>ServiceIntegrationTest.java
```

## Package responsibilities

Ranks in the first column come from `docs/adrs/018-clean-architecture-package-dependencies.md`.
Imports must point at the same rank or lower.

| Rank | Package | Responsibility | Key annotations |
|---|---|---|---|
| 1 | `shared/dto/` | Cross-layer response envelopes (`ApiResponse`, `PageResponse`). | — |
| 1 | `shared/exception/` | Custom exceptions and the global error handler. | `@RestControllerAdvice` |
| 1 | `shared/openapi/` | OpenAPI security-scheme names, so controllers need not import a config class. | — |
| 1 | `shared/validation/` | Reusable constraint annotations and their validators. | `@Constraint` |
| 1 | `enums/` | All enumerations, grouped by domain — including ones that began life nested in an entity. | — |
| 2 | `model/` | JPA entities — the persistent domain model, nothing else. | `@Entity`, `@Table` |
| 3 | `repository/` | Data access via Spring Data JPA interfaces, plus `*Specifications`. | `@Repository` (optional), extends `JpaRepository` |
| 4 | `dto/request/` | Inbound API contracts — what the client sends. | `@Data` (Lombok) |
| 4 | `dto/response/` | Outbound API contracts — what the client receives. | `@Data` (Lombok) |
| 4 | `dto/mapper/` | Entity↔DTO mappers. | MapStruct-generated |
| 5 | `service/` | Business logic, transaction boundaries, orchestration. | `@Service`, `@Transactional` |
| 5 | `service/<domain>/*Properties.java` | `@ConfigurationProperties` live beside the service that reads them, never in `config/`. | `@Component`, `@ConfigurationProperties` |
| 6 | `infrastructure/` | Adapters to external systems (email, embedding, Google, VNPay, JWT). | `@Component`, `@Configuration` (adapter wiring) |
| 7 | `controller/` | HTTP routing, request validation, response mapping. No business logic. | `@RestController`, `@RequestMapping` |
| 7 | `scheduled/` | Scheduled jobs. | `@Component`, `@Scheduled` |
| 8 | `config/` | Composition root: Spring `@Configuration` classes only. Nothing may import it except itself. | `@Configuration` |

### Ports and adapters

The service layer declares the interfaces it needs; `infrastructure/` implements them. An interface
consumed by a service is a *port* and lives in `service/`, so the dependency points inward:

| Port (rank 5) | Adapter (rank 6) |
|---|---|
| `service/outbound/OutboundChannelAdapter` | `infrastructure/email/EmailChannelAdapter` |
| `service/email/EmailService` | `infrastructure/email/SmtpEmailServiceImpl` |
| `service/auth/GoogleIdentityProvider` | `infrastructure/google/GoogleIdentityProviderAdapter` |
| `service/payment/PaymentGateway` | `infrastructure/payment/vnpay/VnpayClient` |

A third-party interface the service consumes directly (for example Spring AI's
`org.springframework.ai.embedding.EmbeddingModel`) is not a project port: it stays as-is and the
service depends on the library, not on `infrastructure/`.

## Conventions

- **Entities are singular:** `User`, not `Users`.
- **REST endpoints are plural:** `/api/users`, not `/api/user`.
- **DTOs at API boundaries:** controllers accept/return DTOs, never entities directly.
- **Constructor injection:** use `@RequiredArgsConstructor` (Lombok), not `@Autowired` on fields.
- **Domain sub-packages:** as features grow, each layer gets domain folders
  (e.g., `controller/user/`, `service/recipe/`). Keep them shallow —
  don't nest deeper than one level within a layer.
- **Enums live in `enums/<domain>/`, never nested in an entity or DTO.**
- **A package may import its own rank or lower, never higher.** Run
  `pwsh tools/rank-check.ps1` after structural changes; it must report zero upward edges.

## Adding a new domain

When building a new feature (e.g., "recipes"):

1. Add `model/recipe/Recipe.java` — the JPA entity.
2. Add `enums/recipe/<Name>.java` for any enumeration the feature needs.
3. Add `repository/recipe/RecipeRepository.java` — the Spring Data interface.
4. Add `dto/request/recipe/CreateRecipeRequest.java` and `dto/response/recipe/RecipeResponse.java`.
5. Add `service/recipe/RecipeService.java` — business logic. Declare any outbound need as a port here.
6. Add `controller/recipe/RecipeController.java` — REST endpoints.
7. Add matching test classes under `src/test/java/com/vegalife/`.
8. Add a Flyway migration in `src/main/resources/db/migration/` for the table.
9. Run `pwsh tools/rank-check.ps1`; it must report zero upward edges.

Each layer can be built and tested independently before wiring them together.
