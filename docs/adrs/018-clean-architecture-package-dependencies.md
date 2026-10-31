# ADR-018: Enforce Clean-Architecture Package Dependencies (Inner Layers Never Depend on Outer Layers)

## Status

Accepted

## Date

2026-10-30

## Deciders

Backend team, on the request to "apply clean arch — inner layer should never
depend on outer layer when tracking all script dependency".

## Context

An import-level dependency graph of every file under
`src/main/java/com/vegalife/` (package → package, counted from `import`
statements) was built and checked against a strict rule: **a package may only
import packages at a strictly lower layer rank.** The `model`, `repository`,
and `dto` packages already satisfied it. The violations found:

| # | Violation | Evidence |
|---|---|---|
| 1 | `shared/security` → `repository`, `service` | `JwtAuthenticationProvider` imports `UserRepository`, `JwtTokenService` — the innermost package depended on the two outermost |
| 2 | `service` ↔ `infrastructure` cycle | `service/auth → infrastructure.google`, `service/payment → infrastructure.payment(.vnpay)`; `infrastructure/email → service.email`, `service.outbound` |
| 3 | `service` ↔ `filter` cycle | `service/post → filter.ContentFilterService`; `filter → service.outbound`, `filter → repository.post/recipe` |
| 4 | `service` → `shared/config` | `AdminDashboardService → CacheConfig`, `MediaService → MediaProperties`, `PaymentCheckoutService → PaymentProperties` |
| 5 | `scheduled` → `repository` directly | jobs bypass `service/` (rank 7 → rank 3 is legal, but noted) |
| 6 | Port on the wrong side | `PaymentGateway` interface lives in `infrastructure/payment` yet is imported by `service/payment` |
| 7 | `service/email` ↔ `service/outbound` | same-rank cycle inside `service/` (tolerated, documented) |
| 8 | Enums co-located with entities | 7 top-level enum files plus 17 nested enums inside `model/` entities |
| 9 | Cross-domain imports within a layer | `service/location → dto.mapper.admin`, `service/ai → dto.request.menu`, `service/subscription → dto.request.admin`, `dto/mapper/admin → dto/mapper/location` |

Two related concerns from the same review:

- "Common code should be reusable **per layer**, not one global `shared/`
  scope." Today `shared/` mixes a true kernel (exceptions, validation,
  `ApiResponse`/`PageResponse`) with Spring `@Configuration` and with security
  adapters that reach outward.
- "DTO used by more than one layer should sit in its own package." Checked:
  `repository/` imports **no** DTO (0 matches); only `service/` and
  `controller/` consume `dto/`, so a single `dto/` package between `repository`
  and `service` is sufficient — no per-layer DTO copies are needed.

## Decision

**Adopt a ranked layer model. Every package gets exactly one rank; imports may
only point to a strictly lower rank.**

| Rank | Package | May import | Notes |
|---|---|---|---|
| 1 | `shared/` (`exception/`, `validation/`, `dto/` → `ApiResponse`, `PageResponse`) and top-level `enums/` | — | The kernel. No internal imports. Holds exception **types** only — never a Spring-web handler. |
| 2 | `model/` (entities only) | 1 | Loses all enum types; nested entity enums move to `enums/<domain>/`. |
| 3 | `repository/` | 1–2 | Unchanged — already compliant. |
| 4 | `dto/` (`request/`, `response/`, `mapper/`) | 1–2 | Single shared DTO layer for `service` + `controller`. |
| 5 | `service/` (absorbs `filter/` as `service/filter/`; owns its ports: `OutboundChannelAdapter`, `EmailService`, new `GoogleIdentityProvider`, relocated `PaymentGateway`) | 1–4 | Defines interfaces; never imports rank 6. |
| 6 | `infrastructure/` (+ `security/` ← `shared/security/`; email, google, vnpay, embedding, **media/Cloudinary** adapters) | 1–5 | Implements rank-5 ports only. Anything importing a vendor SDK lives here. |
| 7 | `controller/` (incl. `advice/`), `scheduled/` | 1–6 | The only entry points; `scheduled/` may reach `repository/` directly. `controller/advice/` holds `@RestControllerAdvice` handlers — HTTP mapping concerns, so they sit here, not in the kernel. |
| 8 | `config/` (Spring `@Configuration` classes only) | 1–7 | Split out of `shared/`. `*Properties` records move down beside their consumer (`MediaProperties → service/media`, `PaymentProperties → service/payment`, `OutboundMessageProperties → service/outbound`). |

Concretely this means:

1. **`shared/security/` moves to `infrastructure/security/`** — it is a
   Spring-Security adapter over `JwtTokenService`/`UserRepository`, i.e. outer
   infrastructure, not kernel code.
2. **`filter/` becomes `service/filter/`** — it is a domain service consumed by
   `service/post` and `scheduled`, and it consumes `service/outbound`'s port and
   `repository/`. Ranking it at 5 removes the `service ↔ filter` cycle.
3. **Ports move to the inner side:** `PaymentGateway` → `service/payment/`;
   a new `GoogleIdentityProvider` interface in `service/auth/` wraps the
   `infrastructure/google` exchanger/verifier. `infrastructure/` keeps only
   implementations.
4. **All enums move out of `model/`** — top-level enum files *and* nested
   entity enums (`User.Role`, `Post.Status`, `Media.Status`, …) into a new
   top-level `enums/<domain>/` package, referenced as `enums.user.Role` etc.
5. **`shared/config/` splits:** `@Configuration` classes → top-level
   `config/`; `*Properties` records move to the rank that consumes them.
6. **Rank 8 is exempt from the "no upward import" check only for
   `@Configuration` wiring** — configuration classes necessarily reference the
   beans they configure.

### Amendment (boundary-cleanup branch)

Two clarifications added while closing the remaining boundary faults this ADR
left open. Both are placement rules, not new decisions — they make explicit
what the rank table already implies.

1. **Kernel purity: exception *types* rank 1, exception *handlers* rank 7.**
   `shared/exception/` keeps `NotFoundException`, `BadRequestException`, the
   `ErrorCode` enum and friends — plain Java, zero framework imports. The
   `@RestControllerAdvice` that maps them to `ResponseEntity` is a Spring-MVC
   concern and therefore lives at `controller/advice/GlobalExceptionHandler`,
   rank 7. Rank 1 had been importing `org.springframework.web` and
   `org.springframework.http`, which made the innermost layer depend on the
   web framework.

2. **Ports/adapters placement is decided by dependency direction, not by
   naming.** A rank-6 class is one that imports a vendor SDK or otherwise
   reaches outside the process. A class that implements a rank-5 port using
   only repositories and other rank-5 services stays at rank 5 — the
   `*Adapter` suffix is then a statement about intent, not about rank.
   Concretely:
   - `infrastructure/media/CloudinaryUploadProvider` (imports
     `com.cloudinary.*`) is rank 6; its port `service/media/
     PresignedUploadProvider` stays rank 5.
   - `service/outbound/ContentFilterOutboundAdapter` and
     `service/outbound/MediaPurgeOutboundAdapter` are rank 5 despite the
     suffix, because they touch only repositories.
   - The same port may legitimately have impls at both ranks
     (`OutboundChannelAdapter` at 5 via the two above, at 6 via
     `EmailChannelAdapter`).

   **Self-correction:** an earlier audit report flagged the two
   `service/outbound/*Adapter` names as misleading. That finding is withdrawn —
   the naming is correct and the report conflated "adapter" with "rank 6".

3. **Mappers live in `dto/mapper/`, with no domain exception.** The rank-4 row
   already lists `mapper/` as part of `dto/`; `service/recipe/
   RecipeSummaryMapper` was the one mapper left behind in `service/`. It moves
   to `dto/mapper/recipe/` so the rule has no carve-out.

### Considered options

- **Option A — Ranked layers with the existing names (chosen)** — assign each
  package a rank, move only the packages that violate the rule, keep
  `controller/service/repository/model` vocabulary. Mechanical, revertible,
  and the rule is machine-checkable afterwards.
- **Option B — Full hexagonal rename (`domain/`, `application/`, `ports/`,
  `adapters/`)** — same dependency rules but renames every layer; far larger
  diff and rewrites every doc/test path, for no additional enforcement.
- **Option C — Status quo + documentation only** — write the rule down without
  moving code; leaves the two cycles (`service ↔ infrastructure`,
  `service ↔ filter`) and the `shared/security` inversion in place, so the
  rule would be false on day one.
- **Option D — Per-layer DTO copies (`service/dto`, `controller/dto`)** —
  rejected after measurement: `repository/` imports no DTO at all, so a single
  `dto/` rank between `repository` and `service` covers both consumers without
  duplicated types.

### Non-goals

- No behavior change: no endpoint, transaction, validation, or persistence
  semantics change. This is package placement + interface extraction only.
- No rename of layer *names* (`controller/service/repository/model` stay);
  this ADR fixes **dependency direction**, not vocabulary.
- No change to the test tree layout beyond mirroring the moved packages.

## Consequences

- The dependency rule becomes mechanically checkable, and that check ships with
  this ADR: `tools/rank-check.ps1` reads the `import` statements under
  `src/main/java` and fails on any edge from a lower rank to a higher one, so
  the rule no longer depends on review discipline.
- `service/` grows port interfaces for Google OAuth and VNPay; adding a second
  provider or gateway becomes an implementation-only change at rank 6.
- Cross-domain imports inside a layer (violation #9) remain permitted — they
  are rank-legal — but new code should prefer extracting shared DTO types into
  the owning domain package rather than reaching across domains.
- The `service/email ↔ service/outbound` same-rank cycle is accepted for now;
  merging `OutboundEmailPayload` into a shared payload type is a possible
  follow-up.
- Large but mechanical diff: ~50–60 main-source moves plus the mirrored test
  tree, delivered as commit-sized phases on one dedicated branch.

**Negative / trade-offs:**

- Every reference site of a nested enum changes from `User.Role.ADMIN` to the
  new import — a wide, churn-heavy diff that is trivial to get wrong in
  review despite being behavior-preserving.
- Two new indirections (`GoogleIdentityProvider`, `PaymentGateway` moved to
  rank 5) add an interface hop to Google login and VNPay checkout paths.
- Same-rank imports remain possible (Java cannot express rank rules by
  itself); the rule only becomes real once an automated check enforces it.

## Verification

The decision is right when an import-level scan of
`src/main/java/com/vegalife/` (package → package, from `import` statements)
reports **zero edges pointing from a lower rank to a higher rank**, and the
existing suite passes unchanged before and after the move.

Concretely, from the repository root:

```
pwsh tools/rank-check.ps1                 # prints "upward edges: 0", exit 0
.\mvnw.cmd test                           # 780 unit tests, 0 failures, 2 skipped
.\mvnw.cmd checkstyle:check               # 0 violations
.\mvnw.cmd spotless:check                 # nothing to change
.\mvnw.cmd verify -Pintegration-test      # 625 integration tests, 0 failures
```

Revisit if a future change needs a documented
exemption beyond rank-8 wiring or the accepted `service/email ↔
service/outbound` same-rank cycle — two exemptions is the point where the
rank model should be re-derived rather than patched.
