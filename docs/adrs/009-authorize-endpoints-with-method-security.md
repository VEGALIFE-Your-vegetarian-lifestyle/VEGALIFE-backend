# ADR-009: Authorize Endpoints with Method-Security Annotations, Not a Central Path List

## Status
Accepted

## Date
2026-10-09

## Deciders
Vegalife backend team

## Context

Authorization for every HTTP endpoint lives in one `authorizeHttpRequests` block in
`src/main/java/com/vegalife/shared/config/SecurityConfig.java`. The rules are path globs:
`/api/auth/**` permitAll, `/api/admin/**` hasRole(ADMIN), a handful of `GET` patterns permitAll,
and a catch-all `.anyRequest().authenticated()`.

Two problems follow from that shape, and both showed up in recent work:

- **The rule is invisible at the endpoint.** To know whether a route is public, authenticated, or
  admin-only, a reviewer must open `SecurityConfig` and reason about pattern precedence. The
  `GET /api/posts/*` change (making post detail public) was exactly this: a one-line controller
  edit required auditing the whole path list, and the `@SecurityRequirement` Swagger annotation had
  to be moved off the class to stay truthful.
- **The default is permissive-by-accident.** A new endpoint nobody remembers to list silently
  inherits `.authenticated()`. That is usually safe, but it is a decision made by omission, and an
  over-broad `permitAll` glob can silently open more than intended.

Method security (`@PreAuthorize`) is the standard Spring Security tool for expressing access next to
the code it protects. The project uses Spring Boot 3.5 / Spring Security 6, where method security is
enabled with `@EnableMethodSecurity` and denial throws `AuthorizationDeniedException`.

## Decision

Move the **role/authentication** rule onto the endpoint:

- `@EnableMethodSecurity` on `SecurityConfig`.
- **Authenticated** endpoints: `@PreAuthorize("isAuthenticated()")` — class-level where the whole
  controller is uniform, method-level where a controller mixes classes.
- **Admin-only** endpoints: `@PreAuthorize("hasRole('ADMIN')")`.
- **Public** endpoints and paths with no controller method (Actuator, OpenAPI/Swagger, the VNPay IPN
  webhook) stay as `permitAll()` path rules in `SecurityConfig` — `permitAll()` is the correct Spring
  idiom there, and public access is a deliberate, reviewable statement best kept in one place.
- The catch-all becomes `.anyRequest().denyAll()`: a forgotten endpoint fails closed (403 to
  everyone) instead of silently inheriting "authenticated".

`SecurityConfig` remains the single place for the filter pipeline, CORS, session policy, the 401
entry point, and these path-level exceptions.

## Considered options

- **Option A — `@PreAuthorize` for roles/auth, `permitAll` path rules for public (chosen).** Rule is
  local and reviewable for the cases that carry risk (who may call a protected endpoint), public
  access stays explicit and centralized, and the fail-closed default stops silent inheritance.
- **Option B — Annotate everything, including public, with `@PreAuthorize("permitAll")`.** Uniform,
  but `permitAll` is a SpEL property with no parentheses and it fights the method-security model
  (which runs only for an existing `Authentication`); public access is clearer as a path rule.
- **Option C — Keep the central path list as-is.** Zero churn, but preserves the cross-file audit and
  the silent default that motivated this change.

## Consequences

- Adding an endpoint no longer requires touching `SecurityConfig`; the rule travels with the method.
- A new endpoint with no annotation is denied by default — louder, and safer, than silent inheritance.
- Unauthenticated requests to protected endpoints must still return `401`, and authenticated-but-
  forbidden requests `403`; the `authenticationEntryPoint` body stays, and an `AccessDeniedHandler`
  403 body is added if the default is not already consistent.
- Behaviour is intended to be unchanged: every endpoint keeps the exact access class it has today.
  This is a move, not a policy change.
