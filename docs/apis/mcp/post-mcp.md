# API Reference: AI Chat Menu Tool Contract

## Overview

The AI chat reaches exactly five weekly-menu capabilities as Spring AI `@Tool`
methods living **inside the backend itself** (ADR-013) — `MenuTools` in
`service/ai/`. There is no separate MCP service, no HTTP hop, and no MCP
transport: each tool calls `MenuService` directly in the same process.

This document is the tool contract — the names, descriptions, parameter
schemas, and result shapes the model sees. It is deliberately identical in
content to what the tools published over MCP, because that contract is the
behavior the refactor preserves.

## How a tool call runs

```text
POST /api/ai/messages (or .../stream)
  └─ authenticated caller → server-derived userId
       └─ AiChatService puts it in the Spring AI ToolContext (AiToolContext.forUser)
            └─ model's tool call dispatched through ToolCallback
                 └─ MenuTools reads the userId from the ToolContext
                      └─ MenuService, scoped by findByIdAndUserId
```

Four steps, one process. The old path — controller JWT header → allowlisted
`ToolContext` → MCP `_meta` → MCP server re-reads it → `Authorization` header →
backend REST call → back — existed only to cross a process boundary, and is
gone with it.

## Identity

Tool calls carry **no credential and no identity parameter**. The `userId` is
put in the `ToolContext` by `AiChatService` from the principal the security
context already validated (BR-MCP-001), and read back by `MenuTools`. Identity
is never a tool argument, so a model cannot select whose data a tool reads by
manipulating parameters (BR-MCP-004).

Every operation is still scoped by `MenuService`, which matches on
`id AND userId`. The tools are not a privilege escalation path: they can reach
exactly the rows the caller could reach through `MenuController`.

Because there is no credential in the chain, a token revoked mid-request
(`blacklist_token`) does not invalidate the in-flight turn. That is the one
behavioral difference from the MCP design, and it is bounded by the streaming
timeout (180s) against a 15-minute token lifetime.

## Tools

Exactly five tools exist (BR-MCP-002). Each read tool mirrors one backend
endpoint with the same parameter names, so a tool call is a mapping rather
than a translation. Request validation is shared with the REST path through
`MenuQueryFilterResolver`, so a tool call and the equivalent HTTP request
reject the same input with the same wording.

### `get_menu` — list the caller's menus

Mirrors `GET /api/menus`.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| from | date `YYYY-MM-DD` | No | Inclusive start of the range. Either bound may be supplied alone. |
| to | date `YYYY-MM-DD` | No | Inclusive end of the range. |
| status | string | No | `drafted`, `scheduled`, `cancelled`, or `completed`. Omit for any status. |
| page | integer | No | Zero-based page index. Omit for `0`. |
| size | integer | No | Menus per page, 1–100. Omit for `20`. |
| sort | string | No | `field,direction`, e.g. `startDate,desc`. |

Behavior: returns the paginated list envelope unchanged, so the result always
says how many menus exist (`totalElements`, `totalPages`) and never silently
truncates. Each item is a **summary** — `id`, `startDate`, `endDate`, `status`,
`notes` — with no meals. Use `get_meal_plan` for the day-by-day dishes.

### `get_meal_plan` — read the caller's meal plan

Mirrors `GET /api/menus/details`.

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| from | date `YYYY-MM-DD` | No | Inclusive start of the range. With neither bound, defaults to the current ISO week (server UTC). |
| to | date `YYYY-MM-DD` | No | Inclusive end of the range. |
| menuId | uuid | No | Read exactly one owned menu's meals. Overrides `from`/`to`. |

Behavior: returns every menu intersecting the range, each with its `days[]`,
`meals[]` and the **full dish behind each meal** (`id`, `name`, `description`,
`imageUrl`, `cuisineType`), ordered by `startDate` ascending. There is no
`status` parameter — each returned menu carries its own `status`, so the caller
can tell a draft from a live plan without a second call. The result is a plain
array, not a page object.

### `create_menu` — create a drafted week

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| startDate | date `YYYY-MM-DD` | Yes | First day of the week (a Monday by convention). `endDate` is derived as `+6`. |
| days | array | Yes | Exactly 7 day objects, each `{date, meals[]}`; meal = `{mealType, dishId, servings}` with `mealType` ∈ `BREAKFAST`/`LUNCH`/`DINNER`, `servings ≥ 1`. |
| notes | string | Yes | A short summary of the week and why these meals were chosen (its theme and the member's relevant restrictions honored, e.g. `High-protein week, peanut-free, built around your saved dishes`). The model must always supply one; the REST contract keeps `notes` optional for HTTP callers. |

Calls `MenuService.createMenu`. Always creates `drafted` (BR-MENU-005); all
structural validation (7 dates, ≤1 meal per type per day, ≥5 distinct dishes,
no dish >3 times, dishes exist) is the backend's. Response: full menu detail
with the new `id`. Dietary preferences are not a menu field — they live on the
member's profile and are already part of the assistant's context.

### `update_menu` — edit the caller's current-week draft

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| menuId | uuid | Yes | The menu to replace. |
| days | array | Yes | The complete new week (same shape as `create_menu`); meals are replaced wholesale. |
| notes | string | Yes | A short summary of the week (same guidance as `create_menu`); it replaces the stored note. The model must always supply one. |

**Tool-layer pre-check (BR-MCP-005):** refused with a readable message unless
the target is the caller's own menu, in the current UTC week, with status
`drafted` — stricter than the REST API, which also allows `scheduled`. Then
`MenuService.updateMenu` re-validates the whole week (BR-MENU-005) and
ownership (BR-MENU-001).

### `schedule_menu` — activate a drafted week

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| menuId | uuid | Yes | The drafted menu to schedule. |

Calls `MenuService.changeStatus(..., "scheduled")`. Per BR-MCP-006 the
assistant confirms the exact week with the user before calling. Response: full
menu detail with `status: scheduled`.

## Responses

Two distinct result shapes, and the model must be able to read both without
JSON-parsing (BR-MCP-007).

**Success** — the backend's `{success, message, data}` envelope, emitted as a
single JSON object (never a quoted/escaped string literal):

```json
{
  "success": true,
  "message": "Menu created successfully",
  "data": { "id": "7c1f2b90-...", "startDate": "2026-10-05", "status": "drafted", "days": [] }
}
```

Success messages are the controller's own: `Menus retrieved successfully`,
`Meal plan retrieved successfully`, `Menu created successfully`,
`Menu updated successfully`, `Menu status updated successfully`.

**Failure** — bare text, not JSON. Most failures are prefixed `error: `; the
BR-MCP-005 pre-check refusal is prefixed `update refused: `.

### Error Responses

| Tool-visible result | Condition | Source |
|---------------------|-----------|--------|
| `error: caller identity missing (no caller identity on this tool invocation)` | No `userId` in the `ToolContext` | tool layer |
| `error: Invalid from 'not-a-date'. Use YYYY-MM-DD.` | Malformed or out-of-range date bound | `MenuQueryFilterResolver` (shared with REST) |
| `error: from must be on or before to` / `error: Date range must not exceed 366 days` | Invalid range | `MenuQueryFilterResolver` (shared with REST) |
| `error: Unknown status 'x'. Use drafted, scheduled, cancelled or completed.` | Bad status filter | `MenuQueryFilterResolver` (shared with REST) |
| `error: menuId must be a UUID` / `error: menuId is required` | Malformed or missing id | tool layer |
| `error: startDate must be a date in YYYY-MM-DD format` | Malformed week start | tool layer |
| `error: days must contain exactly the 7 dates...` (or any BR-MENU-005 message) | Structural validation failed | backend validation |
| `error: Menu not found` | Unknown `menuId` or another user's menu | backend (404-equivalent) |
| `error: Cannot transition a drafted menu to completed` | Illegal status transition | backend (409-equivalent) |
| `error: Another scheduled menu already overlaps this week` | Scheduling conflict | backend (V30 GiST, ADR-010) |
| `update refused: the target must be the caller's own current-week menu with status drafted` | Tool-layer pre-check failed | BR-MCP-005 |

There is no transport failure mode: the tool cannot be unreachable, and there
is no credential to expire in the middle of a call.

## Business Rules

- BR-MCP-001 — tool calls act as the authenticated caller
- BR-MCP-002 — exactly five tools are exposed
- BR-MCP-003 — tool calls consume no AI quota
- BR-MCP-004 — credentials are never model-visible or logged
- BR-MCP-005 — chat edits only the caller's current-week draft
- BR-MCP-006 — scheduling happens only after explicit user confirmation
- BR-MCP-007 — tool failures are model-readable results
- BR-MENU-001..007 — all menu domain rules still apply, unchanged

## Example

A chat turn (server-side flow):

```text
user: "Draft next week with tofu scramble for breakfasts and schedule it"
  → assistant calls create_menu(startDate=2026-10-19, days=[...], notes="Tofu-scramble week, peanut-free")
  → {"success":true,"message":"Menu created successfully","data":{"id":"7c1f2b90-...", ...}}
user: "Yes, schedule it"
  → assistant calls schedule_menu(menuId=7c1f2b90-...)
  → {"success":true,"message":"Menu status updated successfully","data":{...,"status":"scheduled"}}
```

---

## Related

- Decision: `docs/adrs/013-serve-ai-tools-in-process.md` (supersedes
  `docs/adrs/012-multi-module-mcp-tool-surface.md`)
- Feature Spec: `docs/feats/mcp-menu-tools.md`
- Business Rules: `docs/brs/mcp-tools.md`
- Implementation: `src/main/java/com/vegalife/service/ai/MenuTools.java`
- End-to-end test:
  `src/test/java/com/vegalife/integration/ai/MenuToolsEndToEndIntegrationTest.java`
- Backend endpoints: `docs/apis/menus/` (`get-menus.md`, `get-menus-details.md`, `post-menus.md`, `patch-menus.md`, `post-menu-status.md`)
- Shared error/response contract: `docs/apis/error-responses.md`
