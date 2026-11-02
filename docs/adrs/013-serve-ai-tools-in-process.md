# ADR-013: Serve AI Chat Menu Tools In-Process, Retiring the Separate MCP Server

## Status

Accepted

## Date

2026-10-12

## Deciders

Vegalife backend team; direction confirmed with the team owner in-session
(2026-10-12), including the identity approach (authenticated `userId` in
`ToolContext`) and the fate of the Docker end-to-end test.

Supersedes [ADR-012](012-multi-module-mcp-tool-surface.md).

## Context

ADR-012 split the build into a parent reactor plus `api-server` and a separate
`mcp-server`, so that menu operations could be published as MCP tools. The
stated purpose was "a reachable, separately deployable tool surface that
external agents can also consume later" — the AI chat was the first consumer,
not the only anticipated one.

The confirmed requirement has since narrowed to: **the in-repo AI chat must be
able to read and manage a member's weekly menu.** No external MCP client
(Claude Desktop, ChatGPT connectors, another team's agent) is planned. Under
that requirement the separate server buys nothing measurable:

- The model cannot tell the difference. Spring AI flattens tool callbacks into
  the provider's `tools[]` payload; whether those callbacks came from an MCP
  `tools/list` handshake or from local `@Tool` annotations, the JSON the model
  receives is identical, and so is its `tool_calls` response.
- Nothing was ever externally reachable. `docker-compose.yml` binds
  `127.0.0.1:8081:8081` — loopback only, on an internal Docker network. Removing
  the module loses no capability that currently exists.

What the separate server does cost:

- A second service to build, run, test and deploy; CI, compose, Render and the
  CD docker step all name it explicitly.
- An eager-initialization boot dependency: Spring AI's MCP clients initialize at
  startup, so if `mcp-server` is not up when `api-server` boots, chat starts
  with **zero** menu tools while the system prompt still advertises them — the
  model tells members it cannot read their menu rather than failing visibly.
- A JWT round trip, api-server → mcp-server → api-server, replayable within the
  token's 15-minute lifetime if the MCP host leaked.
- ~30 lines of hand-rolled server-side glue (`MetaMergingToolSpecification`) and
  an allowlisting `ToolContextToMcpMetaConverter`, both existing only to work
  around [spring-ai#4773](https://github.com/spring-projects/spring-ai/issues/4773)
  (MCP servers drop request `meta`).

## Decision

Serve the five menu tools (`get_menu`, `get_meal_plan`, `create_menu`,
`update_menu`, `schedule_menu`) from the application process as Spring AI
`@Tool` methods that call `MenuService` directly. Delete the `mcp-server`
module and the MCP client, and restore the single-module build with `src/` at
the repository root.

Caller identity travels as the **authenticated `userId`** already resolved by
`JwtAuthenticationFilter`, carried in the Spring AI `ToolContext` — not as the
caller's JWT. `ToolContext` is never sent to the model (source-verified for
Spring AI 1.1.8, see ADR-012), so identity remains a non-parameter and stays
model-visible neither before nor after this change.

Authentication is unchanged: `/api/ai/**` falls through to
`.anyRequest().authenticated()` and the controller carries
`@PreAuthorize("isAuthenticated()")`. What is removed is a *second* validation
of the same token. This matches the pattern every other controller in the
application already uses — `MenuController` passes `@AuthenticationPrincipal
UUID userId` straight into `MenuService`, which scopes every operation by
owner via `findByIdAndUserId`. The MCP server's re-authentication was an
anomaly forced by process separation, not a security control.

## Considered options

- **Option A — in-process `@Tool` methods over `MenuService`, `userId` in
  `ToolContext` (chosen)** — removes the second process, the boot dependency,
  the token round trip and the #4773 workaround; keeps the tool surface, the
  tool-layer rules and the model-visible contract exactly as they are.
- **Option B — keep `mcp-server`, stop at the internal case** — the status quo.
  Retains every cost above and buys nothing, since no external consumer exists.
- **Option C — keep `mcp-server` and expose it publicly** — the only option that
  would actually deliver the external-agent capability ADR-012 was holding open.
  Requires public HTTPS, authentication and a publishing decision; explicitly
  out of scope, and not a requirement.
- **Option D — keep the caller's JWT in `ToolContext` rather than `userId`** —
  preserves a bearer token in the tool layer for no benefit, and would require
  the tools either to re-authenticate or to trust a parsed token. Rejected: the
  principal is already server-derived and verified.
- **Option E — backend-local `@Tool` methods that still call the REST API** —
  keeps a loopback HTTP hop for no reason now that the caller is in the same
  JVM. Subsumed by Option A.

## Consequences

**Positive:**

- One fewer service; CI, compose, Render and CD revert to a single Dockerfile.
- No boot-ordering dependency: the five tools are always present, so the system
  prompt can no longer advertise tools the runtime does not have.
- No bearer token enters the tool layer, so there is nothing there to log or
  leak.
- The `spring-ai#4773` glue and the meta-allowlisting converter are **deleted**,
  not merely dormant.
- Tool invocation is a method call, not two network hops.

**Negative / trade-offs:**

- Loses the separately publishable tool surface ADR-012 was holding open.
  Re-introducing external MCP later means standing a server back up — but it
  would wrap these same `@Tool` methods, so what is lost is the transport, not
  the tool definitions.
- The tool layer becomes a second path into `MenuService` alongside the REST
  controllers. Mitigated: same JVM and same bean, and `MenuService` still
  enforces ownership on every operation.
- A token revoked mid-request (logout → `blacklist_token`) no longer causes
  subsequent tool calls to fail with 401. Bounded by `SSE_TIMEOUT_MILLIS`
  (180 s) against a 15-minute token lifetime, and identical to the window
  already open for any in-flight REST call.
- The layout restore moves every source file, so the PR exceeds the repo's
  reviewability guide. Splitting it is impractical: no intermediate branch
  would build.

## Verification

- Baseline and post-refactor `mvnw clean verify -Pintegration-test` are
  compared this session. Baseline observed on `151fa40`: 532 unit (1 skipped),
  452 integration (2 skipped), 48 in the then-separate `mcp-server`, all green.
  The `mcp-server` total is expected to disappear, absorbed into the
  application's suites; the other two totals must not regress.
- No test assertion is weakened. `McpMenuEndToEndIntegrationTest` (postgres +
  api-server + mcp-server) is replaced in the same phase by an in-process
  equivalent that keeps its two-caller ownership assertions.
- The five tools' `tools/list` JSON is compared before and after: names,
  descriptions and parameter schemas must be identical.
- **Revisit when** an external agent actually needs the tool surface — that
  reopens a separate MCP server wrapping these methods, and with it the
  cross-domain questions ADR-012 deferred (RFC 8707 audience binding or token
  exchange). Revisit also if Spring AI fixes #4773, though by then it only
  matters if a server has been reintroduced.
