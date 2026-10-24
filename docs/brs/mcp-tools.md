# Business Rules: AI Chat Menu Tool Surface

Constraints on how the AI chat reaches backend capabilities through the
in-process tool layer (issues #17/#18/#19; spec
`docs/feats/mcp-menu-tools.md`; decision
`docs/adrs/013-serve-ai-tools-in-process.md`, which supersedes ADR-012).
Menu domain rules (BR-MENU-*) are unchanged and enforced by the backend
regardless of how a request arrives. The rule IDs keep their `BR-MCP-*`
prefix because that is what existing references point at; the surface they
describe is now the backend's own `@Tool` methods, not an MCP server.

## Rules

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-MCP-001 | Tool calls act as the authenticated caller | Active | 2026-10-12 |
| BR-MCP-002 | Exactly six tools are exposed | Active | 2026-10-25 |
| BR-MCP-003 | Tool calls consume no AI quota | Active | 2026-10-12 |
| BR-MCP-004 | Credentials are never model-visible or logged | Active | 2026-10-12 |
| BR-MCP-005 | Chat edits only the caller's current-week draft | Active | 2026-10-12 |
| BR-MCP-006 | Scheduling happens only after explicit user confirmation | Active | 2026-10-12 |
| BR-MCP-007 | Tool failures are model-readable results, not transport errors | Active | 2026-10-12 |
| BR-MCP-008 | Allergies come from the profile, never from a tool argument | Active | 2026-10-25 |

### BR-MCP-001 — Tool calls act as the authenticated caller
Every menu tool call executes as the member whose authentication produced the
chat turn. The caller's identity travels server-side only: the security context
yields a `userId`, `AiChatService` puts it in the Spring AI `ToolContext`, and
`MenuTools` reads it from there (ADR-013); no tool accepts a user id or any
other identity input, and `MenuService` derives ownership from it
(BR-MENU-001, BR-AI-003). A model cannot select whose data a tool reads by
manipulating arguments.

- Enforcement: `AiToolContext.forUser` (identity placed once per turn by
  `AiChatService`), `MenuTools.callerUserId` (identity read, never accepted as
  a parameter), `MenuService.findByIdAndUserId` (row-level scoping unchanged)
- Related: BR-MENU-001, BR-AI-003, ADR-013
- Added: 2026-10-12
- Amended: 2026-10-13 — the `ToolContext` → MCP `_meta` → `Authorization`
  header chain is gone; identity is now the server-derived principal rather
  than a re-forwarded JWT. The guarantee (no caller-supplied identity input,
  backend owns row scoping) is unchanged.

### BR-MCP-002 — Exactly six tools are exposed
The chat exposes exactly `get_menu`, `get_meal_plan`, `create_menu`,
`update_menu`, `schedule_menu`, and `suggest_dishes`. No other backend
capability is reachable through the tool surface; adding a tool is a reviewed
change to `MenuTools` plus this rule's table, never an emergent consequence of
the backend's endpoint count. The two read tools each mirror one backend
endpoint (`get_menu` → `GET /api/menus`, `get_meal_plan` →
`GET /api/menus/details`) and deliberately do not overlap: one lists
summaries, the other returns the day-by-day meal detail. `suggest_dishes`
mirrors `GET /api/dishes/suggestions`.

- Enforcement: the six `@Tool` methods on `MenuTools` — nothing is registered
  reflectively from the endpoint set, so no controller automatically becomes a
  tool; asserted by test
- Related: ADR-013, FR-002
- Added: 2026-10-12
- Amended: 2026-10-12 — count raised from four to five with `get_meal_plan`,
  and `get_menu` narrowed to a pure list mirror (see
  `docs/feats/meal-plan-detail-query.md`)
- Amended: 2026-10-13 — the registry moved from the `mcp-server` module to
  `MenuTools`; the count and the names are unchanged.
- Amended: 2026-10-25 — count raised from five to six with `suggest_dishes`
  (issue #163). The original rule treated "no dish discovery" as a non-goal;
  the AI could previously only plan meals from dish ids the member had already
  named. `suggest_dishes` closes that gap with a read-only, allergy-filtered
  browse that mirrors an existing member-facing endpoint. See
  `docs/feats/dish-suggestions.md`.

### BR-MCP-008 — Allergies come from the profile, never from a tool argument
No tool and no endpoint accepts an allergen, allergy, or `exclude` parameter.
The member's stored allergies are read server-side from
`user_profile.dietary_preferences` on every call and applied to the result, so
they cannot be omitted by the model, forged by a caller, or drift out of sync
with the profile.

Matching is **exact** on the ingredient name after trimming and case folding:
the stored allergy string must equal an `ingredient.name`. There is no
substring, synonym, plural, or compound matching, and no allergen vocabulary
table. A stored `peanut` therefore does **not** exclude an ingredient named
`peanut butter`. This limitation is deliberate and documented; it is not a bug
to be fixed by adding vocabulary tables.

A dish is suggested only when at least one of its approved recipes is free of
every stored allergy ("block on all"): a dish with some safe variants stays,
with only the safe ones recommended. Dishes with no safe recipe are omitted
entirely.

- Enforcement: `RecipeRecommendationService.allergiesOf` and
  `DishSuggestionService`; asserted by `RecipeRecommendationServiceTest` and
  `DishControllerIntegrationTest`
- Related: `docs/feats/dish-suggestions.md`, ADR-016
- Added: 2026-10-25
- Note: menus already saved are **not** re-checked. A meal keeps its place in a
  saved plan even when nothing can be recommended for it; its
  `recommendedRecipes` is then empty. There is no allergen flag stored on a
  menu, and changing the profile does not rewrite existing menus.

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
No credential exists anywhere in the tool path. The caller's JWT is validated
once at the chat endpoint and never reaches the tool layer — there is no
`Authorization` header to attach, no `_meta` to forward, and no second service
to re-authenticate against. What crosses into the tools is a bare `userId` in
the server-side `ToolContext`, and a tool's parameters, schema, and results
carry menu data only. No prompt, tool parameter schema, tool argument, tool
result, or log line contains a token.

- Enforcement: absence of any credential in the chain (ADR-013), identity
  confined to `AiToolContext.USER_ID_KEY`, review of prompt/tool schemas;
  `MenuToolsEndToEndIntegrationTest` asserts no tool schema declares a
  `jwt`/`token`/`password`/`authorization`/`userId` property
- Related: ADR-013, BR-AUTH-009 (15-minute token lifetime)
- Added: 2026-10-12
- Amended: 2026-10-13 — rule made easier to hold rather than loosened: the
  JWT no longer appears in the tool path at all, so there is no header to set
  or log. Scope widened from "no credential enters a prompt/schema/result" to
  also cover the server-side hop, which no longer carries one.

### BR-MCP-005 — Chat edits only the caller's current-week draft
`update_menu` refuses any menu that is not the caller's own **and** not in the
current (UTC, BR-MENU-004) week **and** not in status `drafted` — a stricter
restriction than the REST API, which allows edits while `scheduled`
(BR-MENU-006). Rejections happen at the tool layer with a message the model
can relay; the backend's own checks still run afterwards.

- Enforcement: `MenuTools.preCheckCurrentWeekDraft` before the service call
  (compares the target's membership in the current server-UTC week's drafted
  set); backend BR-MENU-006 as the backstop
- Related: BR-MENU-004, BR-MENU-006, FR-005
- Added: 2026-10-12
- Amended: 2026-10-13 — pre-check moved from `ToolScope` in the `mcp-server`
  module into `MenuTools`; its conditions and its readable refusal message are
  unchanged.

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
A validation failure, an ownership miss, or an illegal transition is returned
to the model as tool result text carrying the readable message (e.g. `Another
scheduled menu already overlaps this week`), so the assistant can explain or
correct course. Success is the `{success, message, data}` envelope as a single
JSON object; failure is bare `error: ...` text (or, for the BR-MCP-005
pre-check, `update refused: ...`) — never an exception escaping to the caller
and never a transport failure, since there is no transport to fail.

- Enforcement: `MenuTools` catches service and validation exceptions and
  converts them to result text; `RawTextToolResultConverter` emits the
  envelope unescaped so a `String` return is not double-encoded
- Related: ADR-013, `docs/apis/error-responses.md`
- Added: 2026-10-12
- Amended: 2026-10-13 — the backend-call error mapping went with the removed
  HTTP hop; the catch-and-format behavior it implemented is now in `MenuTools`
  itself.
