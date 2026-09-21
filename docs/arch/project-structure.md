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
├── src/
│   ├── main/
│   │   ├── java/           # Application source code
│   │   └── resources/      # Config: application.properties, Flyway migrations
│   └── test/
│       └── java/           # Tests mirroring src/main structure
├── AGENTS.md               # Entry point for AI agents
├── pom.xml                 # Maven build config + dependency management
└── README.md               # Human-facing project README
```

## Source tree

```
src/main/java/com/vegalife/
├── VegalifeApplication.java          # Spring Boot entry point
│
├── controller/                       # REST API layer — thin, no business logic
│   ├── <domain>/                     #   e.g. user/, recipe/, content/
│   │   └── <Domain>Controller.java   #     e.g. UserController.java
│   └── ...
│
├── service/                          # Business logic layer
│   ├── <domain>/                     #   e.g. user/, recipe/, content/
│   │   └── <Domain>Service.java      #     e.g. UserService.java
│   └── ...
│
├── repository/                       # Data access layer (Spring Data JPA)
│   ├── <domain>/                     #   e.g. user/, recipe/, content/
│   │   └── <Domain>Repository.java   #     e.g. UserRepository.java
│   └── ...
│
├── model/                            # JPA entities
│   ├── <domain>/                     #   e.g. user/, recipe/, content/
│   │   └── <Domain>.java            #     e.g. User.java
│   └── ...
│
├── dto/                              # Data Transfer Objects (API boundaries)
│   ├── request/                      #   Inbound DTOs
│   │   └── <Domain>Request.java
│   └── response/                     #   Outbound DTOs
│       └── <Domain>Response.java
│
└── shared/                           # Cross-cutting concerns
    ├── config/                       #   Spring @Configuration classes
    ├── exception/                    #   Custom exceptions + global handler
    │   └── GlobalExceptionHandler.java
    └── util/                         #   Shared utilities (if any)
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

| Package | Responsibility | Key annotations |
|---|---|---|
| `controller/` | HTTP routing, request validation, response mapping. No business logic. | `@RestController`, `@RequestMapping` |
| `service/` | Business logic, transaction boundaries, orchestration. | `@Service`, `@Transactional` |
| `repository/` | Data access via Spring Data JPA interfaces. | `@Repository` (optional), extends `JpaRepository` |
| `model/` | JPA entities — the persistent domain model. | `@Entity`, `@Table` |
| `dto/request/` | Inbound API contracts — what the client sends. | `@Data` (Lombok) |
| `dto/response/` | Outbound API contracts — what the client receives. | `@Data` (Lombok) |
| `shared/config/` | Spring configuration (security, CORS, OpenAPI, etc.). | `@Configuration` |
| `shared/exception/` | Custom exceptions and the global error handler. | `@RestControllerAdvice` |

## Conventions

- **Entities are singular:** `User`, not `Users`.
- **REST endpoints are plural:** `/api/users`, not `/api/user`.
- **DTOs at API boundaries:** controllers accept/return DTOs, never entities directly.
- **Constructor injection:** use `@RequiredArgsConstructor` (Lombok), not `@Autowired` on fields.
- **Domain sub-packages:** as features grow, each layer gets domain folders
  (e.g., `controller/user/`, `service/recipe/`). Keep them shallow —
  don't nest deeper than one level within a layer.

## Adding a new domain

When building a new feature (e.g., "recipes"):

1. Add `model/recipe/Recipe.java` — the JPA entity.
2. Add `repository/recipe/RecipeRepository.java` — the Spring Data interface.
3. Add `dto/request/CreateRecipeRequest.java` and `dto/response/RecipeResponse.java`.
4. Add `service/recipe/RecipeService.java` — business logic.
5. Add `controller/recipe/RecipeController.java` — REST endpoints.
6. Add matching test classes under `src/test/java/com/vegalife/`.
7. Add a Flyway migration in `src/main/resources/db/migration/` for the table.

Each layer can be built and tested independently before wiring them together.
