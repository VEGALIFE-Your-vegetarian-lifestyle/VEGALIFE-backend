---
name: database
description: Use whenever designing entities, writing or reviewing database migrations (Flyway), optimizing queries, changing the schema, or working with Spring Data JPA repositories. Covers entity design, relationships, naming conventions, migration safety, and query patterns. Always checks docs/arch/dependencies.md for approved DB-related dependencies before adding new ones.
---

# Skill: Database

## When to use this skill

Use whenever the task touches the database layer — designing new entities,
writing or reviewing Flyway migrations, changing JPA repositories, writing
or optimizing queries, or adding/modifying database-related configuration.

## Required context / input

- The change being made to the database layer.
- `docs/arch/dependencies.md` — check before adding any DB-related
  dependency (e.g., a new driver, caching library, migration tool).
- Existing entities and migrations in the project.
- `AGENTS.md` — coding guidelines, security constraints (never log or expose
  credentials).

## Procedure

### 1. Entity design

- Place entities in `repository/` package, in a domain sub-package when
  applicable (e.g., `repository/user/User.java`).
- Use `@Entity` with `@Table(name = "snake_case_table_name")`.
- Use `@Id` with `@GeneratedValue(strategy = GenerationType.IDENTITY)` for
  auto-increment PKs (PostgreSQL default).
- Use `@Column` with explicit `name` and `nullable`/`length` where the default
  doesn't match.
- Map relationships with `@ManyToOne`, `@OneToMany`, `@ManyToMany` — avoid
  `@OneToOne` unless truly required (it's often better as a `@ManyToOne`).
- Never put business logic in entities — keep them as data carriers. Business
  logic belongs in services.
- Use Lombok's `@Data` or `@Getter`/`@Setter` to reduce boilerplate, but
  avoid `@ToString` on entities with relationships (circular references).

### 2. Repositories

- Place repositories in the same `repository/` package as their entity.
- Extend `JpaRepository<Entity, IdType>` for standard CRUD.
- Use `@Repository` annotation (optional with Spring Boot, but explicit).
- Name query methods following Spring Data conventions:
  `findByFirstNameAndLastName`, `findByStatusIn`, `deleteByCreatedAtBefore`.
- Use `@Query` with JPQL for complex queries — prefer JPQL over native SQL
  unless performance requires it.
- Use `@Modifying` + `@Query` for update/delete operations.

### 3. Migrations (Flyway)

- Migration files go in `src/main/resources/db/migration/`.
- Naming: `V1__create_user_table.sql`, `V2__add_email_index.sql`.
  - Prefix: `V` + version number (no zero-padding).
  - Separator: double underscore `__`.
  - Description: snake_case, descriptive.
- **Never modify an already-applied migration.** Create a new one instead.
- Every migration must be forward-only — no down/rollback migrations.
- Test migrations against a clean database before committing.
- For data migrations: use separate `R__` repeatable migrations for views,
  stored procedures, or reference data that may change.

### 4. Query patterns

- Prefer derived query methods (e.g., `findByEmail`) for simple queries.
- Use `@Query` JPQL for joins, aggregations, or conditions that derived
  methods can't express.
- Use `Specification` or `Querydsl` for dynamic/composable queries.
- Always add indexes for columns used in `WHERE`, `JOIN`, or `ORDER BY`.
- Avoid N+1 queries — use `@EntityGraph` or `JOIN FETCH` where needed.
- Use pagination (`Pageable`) for list endpoints — never return unbounded
  result sets.

### 5. Application properties

- Database config goes in `application.properties` (or profile-specific
  variants like `application-dev.properties`).
- Use `${ENV_VAR:default}` syntax for secrets — never hardcode credentials.
- Key properties:
  ```
  spring.datasource.url=jdbc:postgresql://localhost:5432/vegalife
  spring.datasource.username=${DB_USER:postgres}
  spring.datasource.password=${DB_PASSWORD:}
  spring.jpa.hibernate.ddl-auto=none
  spring.jpa.show-sql=false
  ```

## Relevant project conventions

- PostgreSQL is the only supported database — do not write portable SQL
  unless explicitly required.
- Schema changes go through Flyway migrations, never through Hibernate
  auto-DDL in production.
- Use `spring.jpa.hibernate.ddl-auto=none` in production profiles — let
  Flyway manage the schema.

## Verification steps

- Migration file follows naming convention (`V{N}__description.sql`).
- Migration is forward-only (no DROP that can't be recreated).
- Entity uses proper JPA annotations with explicit column mappings.
- No hardcoded credentials or secrets in entity/migration code.
- New dependencies checked against `docs/arch/dependencies.md`.
- Queries avoid N+1 patterns where applicable.

## Expected artifacts / output

- Entity class(es) with proper annotations.
- Repository interface extending `JpaRepository`.
- Flyway migration SQL file(s) if schema changed.
- Updated `application.properties` if connection config changed (never
  with hardcoded secrets).
