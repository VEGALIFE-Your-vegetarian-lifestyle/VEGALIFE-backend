# Dependency Matrix

This document tracks which dependencies are approved, planned, and not yet needed for this project. Agents must not add dependencies not listed here without explicit user approval.

## Approved for use (in pom.xml or ready to add)

These are part of the current tech stack. Use freely.

| Dependency | Purpose | Status |
|---|---|---|
| `spring-boot-starter-web` | REST API | In pom.xml |
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ | In pom.xml |
| `spring-boot-starter-data-jpa` | JPA / Hibernate ORM | Planned — next |
| `postgresql` | PostgreSQL JDBC driver | Planned — next |
| `spring-boot-starter-validation` | `@Valid`, `@Email`, `@Size` | Planned — next |
| `springdoc-openapi-starter-webmvc-ui` | Swagger UI / OpenAPI docs | Planned — next |
| `org.projectlombok:lombok` | Boilerplate reduction | Planned — next |
| `org.mapstruct:mapstruct` | Entity ↔ DTO mapping | Planned — next |
| `spring-boot-starter-actuator` | Health checks, metrics | Planned — next |
| `com.h2database:h2` | In-memory test DB | Planned — next |
| `flyway-core` | DB schema migrations | Planned — next |

## Planned (approved, add when a feature needs it)

These are approved in principle. Add them to pom.xml when a feature requires them, not before.

| Dependency | Purpose | Add when |
|---|---|---|
| `spring-boot-starter-security` | Auth, authorization | First feature requiring login/permissions |
| `spring-boot-starter-oauth2-resource-server` | JWT validation | When using JWT-based auth |
| `spring-boot-starter-websocket` | Real-time chat / notifications | Chat feature implementation |
| `com.github.ben-manes.caffeine:caffeine` | Local caching | When hot data needs caching (recipes, feeds) |
| `org.testcontainers:testcontainers` | Real DB in integration tests | When integration tests need real Postgres |
| `io.rest-assured:rest-assured` | Fluent API testing | When API tests become complex |
| `net.logstash.logback:logstash-logback-encoder` | Structured JSON logging | When deploying to cloud with log aggregation |
| `io.micrometer:micrometer-registry-prometheus` | Prometheus metrics | When monitoring is set up |

## Not approved (do not add without asking)

These are common but not needed for this project. Do not add without explicit confirmation.

| Dependency | Why not now |
|---|---|
| `spring-boot-starter-data-mongodb` | Project uses PostgreSQL only |
| `spring-boot-starter-data-redis` | Add only when caching/performance requires it |
| `spring-boot-starter-amqp` | Add only when async messaging is needed |
| `spring-boot-starter-quartz` | Add only when scheduled jobs are needed |
| `io.jsonwebtoken:jjwt` | Prefer Spring Security's built-in JWT support |
| `com.google.guava:guava` | Prefer standard Java / Apache Commons for now |
| `io.github.resilience4j` | Add only when calling unreliable external APIs |

## Adding a new dependency

1. Check this file — is it approved?
2. If yes: add to pom.xml, update this file's status, update AGENTS.md if it becomes part of the core stack.
3. If no: ask the user first. State what it does and why you want it.
4. Never add dependencies for "just in case" — only for an actual current need.
