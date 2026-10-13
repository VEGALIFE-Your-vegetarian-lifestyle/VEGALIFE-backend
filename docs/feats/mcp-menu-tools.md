# Feature Spec: Menu Tools for AI Chat

## Status

Approved

## Author / owner

Backend team; scope confirmed with the product owner (2026-10-12): four menu
tools, JWT-based identity, thin adapters over REST. Extended 2026-10-12 by
`docs/feats/meal-plan-detail-query.md`, which adds `get_meal_plan` (five
tools) and narrows `get_menu`. Re-scoped 2026-10-13 by
`docs/adrs/013-serve-ai-tools-in-process.md`: the capability and the tool
contract are unchanged, but the tools are now `@Tool` methods inside the
backend rather than an MCP server. This file keeps its name because existing
references point at it.

## Summary

Expose the weekly-menu operations (`get_menu`, `get_meal_plan`,
`create_menu`, `update_menu`, `schedule_menu`) as Spring AI `@Tool` methods
on the backend's own tool layer, and wire the existing AI chat so both its
JSON and SSE paths can call them while acting as the logged-in user.

## Problem / motivation

The AI chat (`docs/feats/ai-chat-send.md`) answers from the user's profile and
conversation history only (BR-AI-004). It cannot answer "what's my meal plan
this week?", cannot draft a week of meals into the app, and cannot schedule a
draft — the menu REST surface exists (issues #126/#17/#18/#19, all merged) but
nothing in the chat path can reach it. Users must leave the chat and operate
menus by hand.

## Goals

- The assistant can read the caller's current-week menu and report it, with
  its actual meals, in one chat turn.
- The assistant can create a drafted weekly menu and schedule it on the
  caller's behalf, through confirmation in the conversation.
- Every tool call executes with the caller's identity, enforced by the same
  ownership scoping as a normal API request.
- The tool surface is a small, deliberate allowlist rather than a mirror of the
  backend's endpoints.

## Non-goals

- **No new backend endpoints or domain logic.** All five operations already
  exist; this feature wraps them. (Menu rules stay in BR-MENU-*.)
- No dish/ingredient/recipe/shop discovery tools (`search_dishes` and friends
  are explicitly out — see BR-MCP-002).
- No public tool exposure, no OAuth/authorization server, no agent framework.
- No change to AI quota mechanics (BR-AI-001/002 unchanged).
- No frontend work; the chat UI already posts to the same endpoints.

## Requirements

### Functional Requirements

- [ ] FR-001: The application builds from the root with
      `./mvnw clean verify` as a single Maven module with `src/` at the repo
      root; existing behavior is unchanged.
- [ ] FR-002: The tool layer exposes exactly five tools — `get_menu`,
      `get_meal_plan`, `create_menu`, `update_menu`, `schedule_menu` — as
      `@Tool` methods on `service/ai/MenuTools`, registered with the chat
      client at startup. There is no HTTP tool endpoint. (`get_meal_plan` was
      added by `docs/feats/meal-plan-detail-query.md`, which also narrowed
      `get_menu`; see FR-003.)
- [ ] FR-003: `get_menu` mirrors `GET /api/menus` and nothing else: a
      paginated list of menu summaries with `totalElements`/`totalPages`
      relayed unchanged. It takes no `menuId` and performs no second detail
      call — day-by-day meal detail is `get_meal_plan`'s sole job.
- [ ] FR-004: `create_menu` and `update_menu` take an LLM-composed 7-day
      `days[]` payload plus a required `notes` summary of the week, and call
      `MenuService.createMenu` /
      `MenuService.updateMenu`; all BR-MENU-005 structural validation stays in
      the backend service layer. Dietary preferences are not tool parameters —
      they live on the member's profile.
- [ ] FR-005: `update_menu` refuses, at the tool layer, any menu that is not
      the caller's current-week `drafted` menu (BR-MCP-005).
- [ ] FR-006: `schedule_menu` calls `MenuService.changeStatus(...,
      "scheduled")`; the chat prompt instructs the assistant to confirm the
      week with the user first (BR-MCP-006).
- [ ] FR-007: The caller's identity enters the Spring AI `ToolContext` once
      per turn from the authenticated principal, and is read from it by the
      tools; it is never a tool parameter (BR-MCP-001) and never
      model-visible (BR-MCP-004).
- [ ] FR-008: Both chat paths (`POST /api/ai/messages` and
      `POST /api/ai/messages/stream`) can invoke the tools within one turn.
- [ ] FR-009: Tool-call failures (validation, 404-equivalent, 409-equivalent)
      reach the model as readable tool results (BR-MCP-007).

### Non-Functional Requirements

- [ ] NFR-SEC-001: The tool layer publishes no host, port, or network surface
      of its own — it is method calls inside the already-running API process.
- [ ] NFR-SEC-002: No credential exists in the tool path, and no token appears
      in a log line, a prompt, a tool schema, a tool argument, or a tool result
      (BR-MCP-004).
- [ ] NFR-SEC-003: A tool call cannot affect data the caller could not reach
      directly through the REST API — `MenuService`'s ownership checks remain
      the authority.
- [ ] NFR-MAINT-001: The tool layer contains no business rules; removing it
      leaves the backend fully functional and the menu REST API untouched.
- [ ] NFR-MAINT-002: Tool calls never touch `AiQuotaGuard`; one chat turn is
      exactly one quota unit regardless of tool-call count (BR-MCP-003).

## Design overview

One process. `AiChatService` registers `MenuTools`' `@Tool` methods with the
`ChatClient` at startup via `MethodToolCallbackProvider`, places the caller's
`userId` in the `ToolContext` at the start of each turn, and Spring AI
dispatches the model's tool calls back into those methods. Each tool calls
`MenuService` directly. Full reasoning, alternatives, and the rejected
multi-process option are in `docs/adrs/013-serve-ai-tools-in-process.md`
(which supersedes `docs/adrs/012-multi-module-mcp-tool-surface.md`); the
tool-by-tool contract is in `docs/apis/mcp/post-mcp.md`.

Tool results are exactly two shapes: the backend's `{success, message, data}`
envelope as a single JSON object on success, and bare `error: ...` (or
`update refused: ...`) text on failure, so the model can read either without
parsing.

## Success metrics

- A chat turn asking "what's my meal plan this week?" returns the caller's
  real menu detail (or an honest "none for this week") in the deployed
  environment.
- Issues #17/#18/#19 close with all five tools callable end to end.
- `MenuToolsEndToEndIntegrationTest` is green in the integration profile.

## Acceptance criteria

**As a** logged-in member, **I want to** ask the assistant to read, draft, and
schedule my weekly menu, **so that** I manage my meal plan without leaving the
chat — as myself, with no one else's data reachable.

- [ ] Given a member with a menu this week, when they ask for their plan, then
      `get_menu` returns that menu's 7-day detail and the assistant reports it.
- [ ] Given a member with no menu this week, when they ask for their plan,
      then the assistant says there is none and offers to draft one.
- [ ] Given a member confirms a draft week, when the assistant calls
      `create_menu`, then a `drafted` menu exists for that member only.
- [ ] Given a drafted current-week menu, when the user confirms scheduling,
      then `schedule_menu` transitions it to `scheduled`; when a second
      overlapping week is attempted, the 409 is relayed as an explanation,
      not a crash.
- [ ] Given any tool call, when the backend handles it, then the row it reads
      or writes belongs to the caller (BR-MCP-001) and no other user's menu
      is ever touched.
- [ ] Given a chat turn with 3 tool calls, when it completes, then quota
      increased by exactly 1 (BR-MCP-003).
- [ ] Given the tool registry, when listed, then exactly five tools exist
      (BR-MCP-002).

## Risks / open questions

- Identity for a whole turn is a `userId` carried in the `ToolContext`, not a
  credential re-validated per call. A token revoked mid-turn
  (`blacklist_token`) therefore does not invalidate the rest of that turn.
  Mitigation: bounded by the 180-second streaming timeout against a
  15-minute token lifetime, and no wider than what the REST endpoints already
  accept.
- Prompt-level confirmation (BR-MCP-006) is guidance, not enforcement; the
  backend's overlap/legal-transition rules are the real safety net.
- The tool contract (names, descriptions, parameter schemas, result shapes) is
  intentionally byte-identical to the MCP-era contract. Changing it is a
  reviewed change to `MenuTools` plus BR-MCP-002's table, not a refactor
  side effect.
