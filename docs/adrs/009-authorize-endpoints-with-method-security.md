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

Move the **role/authentication** rule onto the endpoint, and keep **public** endpoints as path rules:

- `@EnableMethodSecurity` on `SecurityConfig`.
- **Authenticated** endpoints: `@PreAuthorize("isAuthenticated()")` where an explicit statement helps
  (mixed controllers); otherwise the path-level `.authenticated()` default covers them.
- **Admin-only** endpoints: `@PreAuthorize("hasRole('ADMIN')")` — class-level on the six admin
  controllers, method-level on `PATCH /api/posts/{postId}/visibility`. No path rule grants ADMIN any
  more, so these annotations are the sole role gate.
- **Public** endpoints stay as `permitAll()` path rules in `SecurityConfig` (`/api/auth/**`, the
  public `GET` routes, the VNPay IPN webhook, Actuator, OpenAPI/Swagger). `permitAll()` is the
  correct Spring idiom for public access and keeps that deliberate decision in one reviewable place.
- The catch-all stays `.anyRequest().authenticated()` — the fail-safe default: an endpoint nobody
  listed is at worst logged-in-only, never open.
- A **guard test** fails the build if a handler is neither on the public allowlist nor carries
  `@PreAuthorize`, so a new endpoint cannot skip the access decision silently.

Note: `.anyRequest().denyAll()` and a `.anyRequest().permitAll()` catch-all were both rejected.
`denyAll()` rejects every request in the filter chain before `@PreAuthorize` is evaluated, so it
disables method security entirely. `permitAll()` lets anonymous requests reach the controller, where
Spring MVC binds and validates the body **before** the method-security interceptor runs — so an
anonymous caller sending a malformed body gets `400`, not `401`, breaking the established contract
(and it makes every unannotated endpoint public). `.authenticated()` returns `401` before MVC and
keeps method security for role checks.

`SecurityConfig` remains the single place for the filter pipeline, CORS, session policy, the 401
entry point, and the public path rules.

## Considered options

- **Option A — `@PreAuthorize` for roles/auth, `permitAll` path rules for public, `.authenticated()`
  catch-all (chosen).** Rule is local for the cases that carry risk (who may call a protected
  endpoint); public access stays explicit and centralized; the fail-safe default plus a guard test
  prevent silent inheritance. Preserves every existing `401`/`403` contract, verified by the suite.
- **Option B — Method security for everything, `permitAll` catch-all, guard test.** One source of
  truth, but provably breaks `anonymous → 401 before validation` (bodies validate before
  `@PreAuthorize`), so it was rejected.
- **Option C — `.denyAll()` catch-all.** Rejected: it blocks requests before method security runs, so
  it cannot coexist with `@PreAuthorize`.
- **Option D — Keep the central path list as-is.** Zero churn, but preserves the cross-file audit and
  the silent default that motivated this change.

## Consequences

- Adding an endpoint no longer requires touching `SecurityConfig`; the rule travels with the method.
- A new endpoint with no annotation is denied by default — louder, and safer, than silent inheritance.
- Unauthenticated requests to protected endpoints must still return `401`, and authenticated-but-
  forbidden requests `403`; the `authenticationEntryPoint` body stays, and an `AccessDeniedHandler`
  403 body is added if the default is not already consistent.
- Behaviour is intended to be unchanged: every endpoint keeps the exact access class it has today.
  This is a move, not a policy change.
