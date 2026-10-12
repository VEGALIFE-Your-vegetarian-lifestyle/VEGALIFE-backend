# Feature Spec: MCP Menu Tools for AI Chat

## Status

Approved

## Author / owner

Backend team; scope confirmed with the product owner (2026-10-12): four menu
tools, JWT-based identity, thin adapters over REST.

## Summary

Expose the four weekly-menu operations (`get_menu`, `create_menu`,
`update_menu`, `schedule_menu`) as MCP tools on an internal MCP server, and
wire the existing AI chat so both its JSON and SSE paths can call them while
acting as the logged-in user.

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
  JWT validation and ownership scoping as a normal API request.
- The MCP surface is a small, deliberate allowlist rather than a mirror of the
  backend's endpoints.

## Non-goals

- **No new backend endpoints or domain logic.** All four operations already
  exist; this feature wraps them. (Menu rules stay in BR-MENU-*.)
- No dish/ingredient/recipe/shop discovery tools (`search_dishes` and friends
  are explicitly out — see BR-MCP-002).
- No public MCP exposure, no OAuth/authorization server, no agent framework.
- No change to AI quota mechanics (BR-AI-001/002 unchanged).
- No frontend work; the chat UI already posts to the same endpoints.

## Requirements

### Functional Requirements

- [ ] FR-001: A multi-module Maven reactor (`api-server`, `mcp-server`) builds
      from the root with `./mvnw clean verify`; `api-server` behavior is
      unchanged.
- [ ] FR-002: The MCP server exposes exactly four tools — `get_menu`,
      `create_menu`, `update_menu`, `schedule_menu` — over Streamable HTTP at
      `/mcp`, port 8081, internal network only.
- [ ] FR-003: `get_menu` returns the caller's matching menu(s); for the
      default (current week) it returns the full 7-day meal detail of the
      best match, not just the list summary.
- [ ] FR-004: `create_menu` and `update_menu` forward an LLM-composed 7-day
      `days[]` payload to `POST /api/menus` / `PATCH /api/menus/{menuId}`;
      all BR-MENU-005 structural validation stays in the backend.
- [ ] FR-005: `update_menu` refuses, at the tool layer, any menu that is not
      the caller's current-week `drafted` menu (BR-MCP-005).
- [ ] FR-006: `schedule_menu` calls `POST /api/menus/{menuId}/status` with
      `scheduled`; the chat prompt instructs the assistant to confirm the
      week with the user first (BR-MCP-006).
- [ ] FR-007: The caller's identity travels `ToolContext` → MCP `_meta` →
      `Authorization` on the backend call; it is never a tool parameter
      (BR-MCP-001) and never model-visible (BR-MCP-004).
- [ ] FR-008: Both chat paths (`POST /api/ai/messages` and
      `POST /api/ai/messages/stream`) can invoke the tools within one turn.
- [ ] FR-009: Backend tool-call errors (400/404/409) reach the model as
      readable tool results (BR-MCP-007).

### Non-Functional Requirements

- [ ] NFR-SEC-001: The MCP server publishes no host port (or `127.0.0.1`
      only for local debugging) and is reachable only on the internal Docker
      network.
- [ ] NFR-SEC-002: The JWT appears in no log line in either service, and in
      no prompt, tool schema, tool argument, or tool result (BR-MCP-004).
- [ ] NFR-SEC-003: A tool call cannot affect data the caller could not reach
      directly through the REST API — the backend's ownership checks remain
      the authority.
- [ ] NFR-MAINT-001: The MCP server contains no business rules; removing it
      leaves the backend fully functional.
- [ ] NFR-MAINT-002: Tool calls never touch `AiQuotaGuard`; one chat turn is
      exactly one quota unit regardless of tool-call count (BR-MCP-003).

## Design overview

Two containers on one internal Docker network. The backend keeps the chat and
the data; the MCP server translates tool calls into REST calls against
`BACKEND_URL`. Identity is the caller's own JWT carried through Spring AI
`ToolContext` and MCP `_meta`, merged server-side by a small wrapper because
Spring AI drops `meta` at the MCP server boundary
(spring-projects/spring-ai#4773). Full reasoning, alternatives, and the
fallback path are in `docs/adrs/012-multi-module-mcp-tool-surface.md`; the
tool-by-tool contract is in `docs/apis/mcp/post-mcp.md`.

## Success metrics

- A chat turn asking "what's my meal plan this week?" returns the caller's
  real menu detail (or an honest "none for this week") in the deployed
  environment.
- Issues #17/#18/#19 close with all four tools callable end to end.
- The end-to-end Docker test (postgres + api-server + mcp-server) is green in
  the integration profile.

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
      or writes belongs to the JWT's user (BR-MCP-001) and no other user's
      menu is ever touched.
- [ ] Given a chat turn with 3 tool calls, when it completes, then quota
      increased by exactly 1 (BR-MCP-003).
- [ ] Given the tool registry, when listed, then exactly four tools exist
      (BR-MCP-002).

## Risks / open questions

- Spring AI #4773 remains open upstream; the server-side `meta` merge is our
  own ~30-line wrapper. Mitigation: isolated in one class with a deletion
  note; runtime-proven by the phase 3 spike and phase 7 test.
- The identity chain spans two processes; a config mistake (e.g. a tool
  forgetting to attach the header) would fail closed as 401 — visible, but
  worth an explicit test per tool.
- Prompt-level confirmation (BR-MCP-006) is guidance, not enforcement; the
  backend's overlap/legal-transition rules are the real safety net.
