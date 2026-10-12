# ADR-012: Expose Menu Operations as an Internal MCP Server With Caller-Identity Propagation

## Status

Accepted

## Date

2026-10-12

## Deciders

Vegalife backend team; direction confirmed with the product owner in-session
(2026-10-12), including the tool scope (four menu tools) and the identity
approach.

## Context

The AI chat feature (`docs/feats/ai-chat-send.md`, BR-AI-001..005) can reply
to users but cannot reach application data: `AiChatService` has no tool
calling, so a user cannot ask the assistant to read or manage their weekly
menu. Issues #17/#18/#19 ask for the menu operations built on the menu REST
surface (`docs/apis/menus/`) to be reachable from chat.

Constraints that shaped the options:

- **Single trust domain.** The backend both mints the user's JWT and owns the
  menu data. There is no second identity provider and no external API in the
  loop. Everything except the backend API stays on an internal Docker network.
- **Stack.** Java 21, Spring Boot 3.5.5, Spring AI 1.1.8 (BOM already managed
  in the root pom, model starter already used by the AI chat).
- **Security posture.** MCP security best practice (2025-11-25 spec and the
  MCP security best-practices doc) calls *token passthrough* an anti-pattern
  when the upstream API lives in a **different** trust domain — the confused
  deputy problem, addressed there via RFC 8707 audience binding or token
  exchange. That guidance targets MCP servers calling third-party APIs
  (GitHub, Slack, Google). Our upstream is the token's own issuer.

Source-verified Spring AI 1.1.8 / `main` facts (read 2026-10-12):

- `ToolContext` is never sent to the model — the framework sends only tool
  definitions across; context reaches the tool directly at invocation time.
- **Client side works:** `SyncMcpToolCallback.call()` converts the
  `ToolContext` to `CallToolRequest._meta` through the pluggable
  `ToolContextToMcpMetaConverter` (default converter excludes the MCP exchange
  key and null values).
- **Server side drops it:** `McpToolUtils.toSharedSyncToolSpecification`
  builds `new ToolContext(Map.of(TOOL_CONTEXT_MCP_EXCHANGE_KEY, ...))` and
  never reads `request.meta()`. Tracked as
  [spring-projects/spring-ai#4773](https://github.com/spring-projects/spring-ai/issues/4773),
  open (`waiting-for-triage`) and unfixed on `main` as of 2026-10-12.
- **Spring AI 2.0.x does not help:** 2.0.0 GA (2026-06-12) and 2.0.1 require
  Spring Boot 4.1 / Framework 7 (we are on Boot 3.5.5), and the `main` branch
  still drops `meta` server-side. `spring-ai-model` 2.0.0 also carries
  CVE-2026-59889 (fixed in 2.0.1).

## Decision

1. **Restructure into a multi-module Maven reactor**: a root parent pom
   (packaging `pom`) with modules `api-server` (the existing application,
   behavior unchanged, renamed from `backend` so it pairs with `mcp-server`)
   and `mcp-server` (new). `mvnw`/`.mvn` stay at the root.
2. **The MCP server is a thin REST adapter** over the backend (`BACKEND_URL`).
   It holds no business rules and no data access; every menu rule and
   ownership check stays in the backend, reached through the existing REST
   endpoints. It runs as a separate container on an internal Docker network,
   port 8081, Streamable HTTP at `/mcp`, never publicly exposed.
3. **Caller identity travels as the user's own JWT**:
   backend chat endpoint validates it → `AiChatService` places it in Spring AI
   `ToolContext` (server-side only) → an allowlisting
   `ToolContextToMcpMetaConverter` serializes only the identity key to MCP
   `_meta` → our own `SyncToolSpecification` wrapper on the MCP server merges
   `request.meta()` back into the `ToolContext` (the #4773 gap) → the tool
   attaches `Authorization: Bearer <jwt>` to the backend REST call. Identity
   is never a tool parameter and never model-visible.
4. **Stay on Spring AI 1.1.8.**
5. **Expose exactly four tools**: `get_menu`, `create_menu`, `update_menu`,
   `schedule_menu` (BR-MCP-002). Dish/recipe/shop discovery is out of scope
   for this branch.
6. **Fallback, only if the runtime spike disproves the chain**: an internal
   service credential plus a trusted `X-User-Id` header, permitted only
   because the backend still derives and enforces ownership server-side
   (BR-MENU-001). Whichever outcome occurs is recorded under "Spike outcome"
   below.

## Considered options

- **Option A — internal MCP server, caller JWT over `_meta` (chosen)** —
  reuses the existing auth stack unchanged, keeps per-user attribution real,
  no privileged secret in the MCP server, same trust domain so forwarding the
  issuer's own token is not a confused-deputy violation.
- **Option B — MCP server linked to the backend service layer** (shared
  module, no HTTP hop) — simplest identity story, but couples the modules and
  abandons the REST contract, duplicating access paths into the domain.
- **Option C — service credential + `X-User-Id` as primary** — avoids any
  Spring AI plumbing risk, but puts a privileged act-as-anyone secret in the
  MCP server and replaces verified identity with a plain header the backend
  must trust.
- **Option D — token exchange / OAuth authorization server (RFC 8707)** — the
  "right" answer for cross-domain MCP; heavyweight (a new IdP) with no
  benefit inside one trust domain.
- **Option E — upgrade to Spring AI 2.0.x for propagation** — rejected: forces
  Spring Boot 4.1, does not fix #4773 anyway, and pulls in a CVE on 2.0.0.
- **Option F — no MCP, backend-local `@Tool` methods only** — less machinery,
  but the plan's goal is a reachable, separately deployable tool surface that
  external agents can also consume later.

## Consequences

**Positive:**

- The backend stays the single source of truth: the MCP server cannot do
  anything a normal authenticated REST caller cannot do.
- No new identity system, no privileged shared secret on the happy path.
- The tool surface is a deliberate allowlist — adding a tool is a reviewable
  change, not an accident.
- The reactor split is mechanical; behavior is proven unchanged before any
  MCP code lands (phase 1 gate).

**Negative / trade-offs:**

- ~30 lines of hand-rolled server-side glue (`meta` → `ToolContext`) diverge
  from Spring AI's auto-conversion. **Deletable** once #4773 is fixed upstream;
  noted in code comments.
- The JWT crosses api-server → mcp-server → api-server, so a token leaked from the
  MCP server is replayable within its 15-minute lifetime (BR-AUTH-009) —
  mitigated by internal-only networking and never logging it.
- Two services to build, run, and test; CI, Docker, Render, and compose paths
  all change in the restructure.
- `update_menu` is deliberately stricter at the tool layer than the REST API
  (current week + drafted only, BR-MCP-005): chat cannot edit last week's or
  another period's menu even though the REST API could.

## Verification

- **Phase 3 spike (runtime):** with `api-server` + `mcp-server` running, a chat
  turn carrying a real JWT invokes a tool and only that user's menu rows are
  returned; the token appears in no log line.
- **Phase 7:** an end-to-end Docker test (postgres + api-server + mcp-server)
  seeds a user, calls `/mcp` with a real JWT, and asserts the persisted menu.
- **Revisit when:** Spring AI fixes #4773 (replace the wrapper with
  auto-configuration), on the Spring Boot 4 migration, or if the MCP server
  ever needs to call a third-party API (which reopens Option D).

## Spike outcome

Source-level verification completed 2026-10-12 (see Context): client-side
`_meta` emission is present in v1.1.8; the server-side `meta` drop is present
on `main`.

**Runtime result (2026-10-12, phase 3): the primary chain works — Option A
confirmed, no fallback needed.** Against a running pair (backend on 8080
with its own database, mcp-server on 8081), a temporary probe tool invoked
over Streamable HTTP with the caller's JWT in `_meta`:

- called back into `GET /api/menus` with `Authorization: Bearer <jwt>` and
  returned member A's menu, while the same call carrying member B's JWT
  returned B's empty list — ownership is enforced by the backend, not by the
  MCP server;
- with no identity in `_meta`, returned a readable "caller identity missing"
  tool result and never called the backend unauthenticated;
- left the JWT out of both servers' logs at default levels;
- appeared exactly once in `tools/list`.

Two findings from the run are encoded in the implementation: Spring AI's
`MethodToolCallback` JSON-encodes `String` results, so the wrapper unwraps a
JSON string literal once to keep the documented response envelope verbatim;
and a `List<ToolCallback>` contribution can be resolved both directly and
through the grouped `ObjectProvider`, so registration de-duplicates by tool
name. The probe tool was temporary; phase 7's Docker end-to-end test
re-proves this chain in CI.
