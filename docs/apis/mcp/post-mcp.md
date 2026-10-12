# API Reference: MCP Tools over POST /mcp

## Overview

The MCP (Model Context Protocol) server exposes exactly four weekly-menu
tools to the AI chat over Streamable HTTP. It is a separate internal service
(ADR-012): each tool translates to a backend REST call made **as the calling
user**. This document is the tool contract the chat and any future MCP client
consumes.

## Endpoint

```text
POST /mcp
```

Streamable HTTP MCP endpoint served by the `mcp-server` module (Spring AI MCP
server), container port `8081`, internal Docker network only — no public
route. Health: `/actuator/health`.

## Authentication

Tool calls carry **no credential of their own**. The member's JWT enters at
the chat endpoint (`POST /api/ai/messages`), travels server-side through
Spring AI `ToolContext` → MCP `_meta`, and is attached as
`Authorization: Bearer <jwt>` on the backend REST call the tool makes
(BR-MCP-001). Identity is never a tool parameter (BR-MCP-004).

An MCP client calling `/mcp` directly (as the end-to-end test does) must
supply the identity through the same `ToolContext`/`_meta` channel.

## Tools

Exactly four tools exist (BR-MCP-002). Results and errors are the backend's
standard `{success, message, data}` envelopes, relayed as tool result text.

### `get_menu` — read the caller's menu(s)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| week | date `YYYY-MM-DD` | No | Any day in the target ISO week. Omit for the current week (server UTC). |
| status | string | No | `drafted`, `scheduled`, `cancelled`, or `completed`. Omit for any status. |
| menuId | uuid | No | Read one specific owned menu directly (`GET /api/menus/{menuId}`), skipping discovery. |

Behavior: resolves the week window server-side, calls
`GET /api/menus?period=week&date=...&status=...`, and — when exactly one menu
matches — returns its **full 7-day detail** (the `GET /api/menus/{menuId}`
payload: days, meals, dish names, servings). Zero matches returns an explicit
"no menu for that week" result; multiple matches returns the list summary
(`id`, `startDate`, `endDate`, `status`, `notes`) so the caller can narrow.

### `create_menu` — create a drafted week

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| startDate | date `YYYY-MM-DD` | Yes | First day of the week (a Monday by convention). `endDate` is derived as `+6`. |
| days | array | Yes | Exactly 7 day objects, each `{date, meals[]}`; meal = `{mealType, dishId, servings}` with `mealType` ∈ `BREAKFAST`/`LUNCH`/`DINNER`, `servings ≥ 1`. |
| dietaryPreferences | string | No | Free-text dietary preference for the week. |
| notes | string | No | Free-text note. |

Forwards to `POST /api/menus`. Always creates `drafted` (BR-MENU-005); all
structural validation (7 dates, ≤1 meal per type per day, ≥5 distinct dishes,
no dish >3 times, dishes exist) is the backend's. Response: full menu detail
with the new `id`.

### `update_menu` — edit the caller's current-week draft

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| menuId | uuid | Yes | The menu to replace. |
| days | array | Yes | The complete new week (same shape as `create_menu`); meals are replaced wholesale. |
| dietaryPreferences | string | No | New value; omit to keep. |
| notes | string | No | New value; omit to keep. |

**Tool-layer pre-check (BR-MCP-005):** refused with a readable message unless
the target is the caller's own menu, in the current UTC week, with status
`drafted` — stricter than the REST API, which also allows `scheduled`. Then
forwards to `PATCH /api/menus/{menuId}`; the backend re-validates the whole
week (BR-MENU-005) and ownership (BR-MENU-001).

### `schedule_menu` — activate a drafted week

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| menuId | uuid | Yes | The drafted menu to schedule. |

Forwards to `POST /api/menus/{menuId}/status` with `{"status":"scheduled"}`.
Per BR-MCP-006 the assistant confirms the exact week with the user before
calling. Response: full menu detail with `status: scheduled`.

## Responses

Success returns the backend envelope as tool result text, e.g.:

```json
{
  "success": true,
  "message": "Menu created successfully",
  "data": { "id": "7c1f2b90-...", "startDate": "2026-10-05", "status": "drafted", "days": [] }
}
```

### Error Responses

Backend HTTP errors are mapped to readable tool results (BR-MCP-007), not MCP
transport failures:

| Tool-visible result | Condition | Source |
|---------------------|-----------|--------|
| `days must contain exactly the 7 dates...` (or any BR-MENU-005 message) | Structural validation failed | 400 from `POST/PATCH /api/menus` |
| `Menu not found` | Unknown `menuId` or another user's menu | 404 |
| `Cannot transition a drafted menu to completed` | Illegal status transition | 409 |
| `Another scheduled menu already overlaps this week` | Scheduling conflict | 409 (V30 GiST, ADR-010) |
| `update_menu refused: only your current week's drafted menu can be edited in chat` | Tool-layer pre-check failed | BR-MCP-005 |
| `Unauthorized` | JWT missing/expired at the backend | 401 |
| `MCP server cannot reach the backend` | `BACKEND_URL` down / timeout | transport-level |

## Business Rules

- BR-MCP-001 — tool calls act as the authenticated caller
- BR-MCP-002 — exactly four tools are exposed
- BR-MCP-003 — tool calls consume no AI quota
- BR-MCP-004 — credentials are never model-visible or logged
- BR-MCP-005 — chat edits only the caller's current-week draft
- BR-MCP-006 — scheduling happens only after explicit user confirmation
- BR-MCP-007 — tool failures are model-readable results
- BR-MENU-001..007 — all menu domain rules still apply, unchanged

## Example

A chat turn (server-side flow, JWT omitted for brevity):

```text
user: "Draft next week with tofu scramble for breakfasts and schedule it"
  → assistant calls create_menu(startDate=2026-10-19, days=[...])
  → 201 drafted menu, id 7c1f2b90-...
user: "Yes, schedule it"
  → assistant calls schedule_menu(menuId=7c1f2b90-...)
  → 200 status=scheduled
```

---

## Related

- Feature Spec: `docs/feats/mcp-menu-tools.md`
- Business Rules: `docs/brs/mcp-tools.md`
- Decision: `docs/adrs/012-multi-module-mcp-tool-surface.md`
- Backend endpoints: `docs/apis/menus/` (`get-menus.md`, `get-menus-menuid.md`, `post-menus.md`, `patch-menus.md`, `post-menu-status.md`)
- Shared error/response contract: `docs/apis/error-responses.md`
