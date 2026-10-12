# Business Rules: MCP Tool Surface

Constraints on how the AI chat reaches backend capabilities through the MCP
server (issues #17/#18/#19; spec `docs/feats/mcp-menu-tools.md`; decision
`docs/adrs/012-multi-module-mcp-tool-surface.md`). Menu domain rules
(BR-MENU-*) are unchanged and enforced by the backend regardless of how a
request arrives.

## Rules

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-MCP-001 | Tool calls act as the authenticated caller | Active | 2026-10-12 |
| BR-MCP-002 | Exactly five menu tools are exposed | Active | 2026-10-12 |
| BR-MCP-003 | Tool calls consume no AI quota | Active | 2026-10-12 |
| BR-MCP-004 | Credentials are never model-visible or logged | Active | 2026-10-12 |
| BR-MCP-005 | Chat edits only the caller's current-week draft | Active | 2026-10-12 |
| BR-MCP-006 | Scheduling happens only after explicit user confirmation | Active | 2026-10-12 |
| BR-MCP-007 | Tool failures are model-readable results, not transport errors | Active | 2026-10-12 |

### BR-MCP-001 — Tool calls act as the authenticated caller
Every MCP tool call executes as the member whose JWT authenticated the chat
turn. The caller's identity travels server-side only (Spring AI `ToolContext`
→ MCP `_meta` → `Authorization` header on the backend call, ADR-012); no tool
accepts a user id or any other identity input, and the backend continues to
derive identity from the validated JWT (BR-MENU-001, BR-AI-003). A model
cannot select whose data a tool reads by manipulating arguments.

- Enforcement: `AiToolContext` (api-server), allowlisting
  `ToolContextToMcpMetaConverter` + `SyncToolSpecification` meta merge
  (mcp-server), `BackendClient` authorization header; backend JWT filter
  unchanged
- Related: BR-MENU-001, BR-AI-003, ADR-012
- Added: 2026-10-12

### BR-MCP-002 — Exactly five menu tools are exposed
The MCP server publishes exactly `get_menu`, `get_meal_plan`, `create_menu`,
`update_menu`, and `schedule_menu`. No other backend capability is reachable
through the MCP surface; adding a tool is a reviewed change to the tool
registry plus this rule's table, never an emergent consequence of the backend's
endpoint count. The two read tools each mirror one backend endpoint
(`get_menu` → `GET /api/menus`, `get_meal_plan` → `GET /api/menus/details`)
and deliberately do not overlap: one lists summaries, the other returns the
day-by-day meal detail.

- Enforcement: the tool registry in `mcp-server` (explicit registration, no
  endpoint reflection); asserted by test
- Related: ADR-012, FR-002
- Added: 2026-10-12
- Amended: 2026-10-12 — count raised from four to five with `get_meal_plan`,
  and `get_menu` narrowed to a pure list mirror (see
  `docs/feats/meal-plan-detail-query.md`)

### BR-MCP-003 — Tool calls consume no AI quota
Menu tool calls are steps inside one chat turn, not separate turns: they never
invoke `AiQuotaGuard` and never increment `ai_usage`. One chat turn that
triggers N tool calls counts exactly one unit, so BR-AI-001/002 keep their
current meaning. A turn whose tool call fails still completes and still counts
as one unit only if the assistant reply is produced.

- Enforcement: `AiChatService` (quota guard runs once per turn, before the
  provider call, unchanged); tools contain no quota code
- Related: BR-AI-001, BR-AI-002, BR-SUBS-001
- Added: 2026-10-12

### BR-MCP-004 — Credentials are never model-visible or logged
The caller's JWT never enters a prompt, a tool parameter schema, a tool
argument, a tool result, or any log line (neither backend nor mcp-server). It
exists only in server-side `ToolContext`/`_meta` and in the `Authorization`
header of the backend call. Tool results carry menu data only.

- Enforcement: allowlisting `ToolContextToMcpMetaConverter` (only the identity
  key crosses the wire), `BackendClient` (header set, never logged), review
  of prompt/tool schemas
- Related: ADR-012, BR-AUTH-009 (15-minute token lifetime)
- Added: 2026-10-12

### BR-MCP-005 — Chat edits only the caller's current-week draft
`update_menu` refuses any menu that is not the caller's own **and** not in the
current (UTC, BR-MENU-004) week **and** not in status `drafted` — a stricter
restriction than the REST API, which allows edits while `scheduled`
(BR-MENU-006). Rejections happen at the tool layer with a message the model
can relay; the backend's own checks still run afterwards.

- Enforcement: `ToolScope` (mcp-server) before the REST call; backend
  BR-MENU-006 as the backstop
- Related: BR-MENU-004, BR-MENU-006, FR-005
- Added: 2026-10-12

### BR-MCP-006 — Scheduling happens only after explicit user confirmation
`schedule_menu` must not be called until the assistant has stated the exact
week being scheduled and the user has confirmed in the conversation. This is
prompt-enforced guidance, not a server-side guarantee — the backend still
applies BR-MENU-003/007 (legal transitions, no overlapping scheduled week).

- Enforcement: chat system prompt (`ChatPrompt` guidance); server-side
  backstop is the backend's status endpoint
- Related: BR-MENU-003, BR-MENU-007, FR-006
- Added: 2026-10-12

### BR-MCP-007 — Tool failures are model-readable results, not transport errors
A 400/401/404/409/5xx from the backend is returned to the model as a tool
result containing the readable message (e.g. `Another scheduled menu already
overlaps this week`), so the assistant can explain or correct course. Only an
MCP-level infrastructure failure (server unreachable, protocol error) surfaces
as a tool/transport error.

- Enforcement: `BackendClient` error mapping (status + envelope message →
  tool result text), `MenuTools`/`MenuWriteTools` catch mapping
- Related: ADR-012, `docs/apis/error-responses.md`
- Added: 2026-10-12
