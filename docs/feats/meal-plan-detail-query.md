# Feature Spec: Meal Plan Detail Query

## Status

Implemented

## Author / owner

Backend team; scope confirmed with the product owner (2026-10-12) across five
clarifying rounds. Driven by #126.

## Summary

Add `GET /api/menus/details`, which returns every one of the caller's menus
intersecting a date range **with its full day-by-day meal detail and embedded
dish information** in one call, and expose it to the AI chat as a fifth MCP
tool, `get_meal_plan`. Simultaneously remove the `period`/`date`/`month`
selectors from the menu read surface and delete `GET /api/menus/{menuId}`,
which `/details?menuId=` supersedes.

## Problem / motivation

Issue #126 shipped two menu reads that split one question across two calls:

- `GET /api/menus` returns `MenuSummaryResponse` — `id`, `startDate`,
  `endDate`, `status`, `notes`, timestamps. **No `days[]`, no meals.**
- `GET /api/menus/{menuId}` returns the meals, but only for one menu.

The MCP `get_menu` tool papers over the split with a "deepen" step that only
fires when the list returns **exactly one** match (`MenuTools.deepenUniqueMatch`).
Two or more menus in a week — a `drafted` and a `scheduled` overlapping, say —
fall through to the raw summary payload, so the assistant answers "what's my
meal plan?" without a single dish in it. One menu in a week also costs two
round trips.

Separately, the `period` selector is redundant and hostile to an LLM caller.
`from`/`to` are accepted only under `period=custom`; sending them bare is a
400 (`MenuQueryFilterResolver.rejectExplicitBounds`). So the most natural
request — "give me 2026-09-01 to 2026-10-15" — is exactly the one that fails.

## Goals

- One call returns every menu in a range, each with its full `days[]/meals[]`
  and enough dish detail to describe the food (not just an id).
- The query surface is a single flat `from`/`to` pair; no mode selector to
  get wrong.
- The AI assistant can read a whole meal plan — defaulting to the current
  week — in exactly one tool call.
- `get_meal_plan` maps 1:1 onto the REST endpoint, so the tool contract is a
  mirror, not a translation.

## Non-goals

- No change to any write path — create, update and status transitions keep
  returning `MenuDetailResponse` unchanged (BR-MENU-005/006/007).
- No pagination on `/details`; a caller that needs paging uses `GET /api/menus`.
- No per-dish or per-ingredient search across menus (BR-MCP-002).
- No nutrition aggregation — `dish` carries no nutrition columns.
- No per-member timezone; BR-MENU-004's server-UTC clock stands.

## Requirements

### Functional Requirements

- [x] FR-001: `GET /api/menus` accepts `from`, `to`, `status`, `page`, `size`
      and `sort`. The `period`, `date` and `month` parameters are removed.
- [x] FR-002: On `GET /api/menus`, `from` and `to` are each independently
      optional: `from` alone bounds the range from below, `to` alone from
      above, and neither leaves the window unbounded. `from > to` and a range
      over 366 days are 400s, unchanged from #126.
- [x] FR-003: `GET /api/menus/details` accepts `from`, `to` and `menuId`, all
      optional.
- [x] FR-004: When `menuId` is supplied to `/details`, it alone selects the
      menu — `from`, `to` and `status` are ignored — and the response is that
      one menu's detail. A missing or foreign id is a 404 (BR-MENU-001).
- [x] FR-005: When `menuId` is absent and neither `from` nor `to` is supplied,
      `/details` defaults to the current ISO week (Monday–Sunday, server UTC).
- [x] FR-006: When `from` and/or `to` is supplied to `/details`, the window is
      applied as in BR-MENU-002 (intersection, inclusive endpoints, each bound
      independently optional).
- [x] FR-007: `/details` returns every matching menu with its `days[]`, each
      day carrying its `meals[]`, each meal carrying `mealType`, `servings`
      and an embedded `dish` object of `{id, name, description, imageUrl,
      cuisineType}`.
- [x] FR-008: `/details` returns `ApiResponse<List<MenuMealPlanResponse>>` —
      a plain list, no page metadata — ordered by `startDate` ascending.
- [x] FR-009: `GET /api/menus/{menuId}` is removed; `/details?menuId=` is its
      only replacement. The path stays registered for `PATCH` and
      `POST .../status`, so a `GET` on it is `405 Method Not Allowed` rather
      than `404` — what's gone is the method, not the route.
- [x] FR-010: The MCP server exposes a fifth tool, `get_meal_plan(from, to,
      menuId)`, whose parameters map 1:1 onto `/details` with no defaults or
      translation applied at the tool layer.
- [x] FR-011: `get_menu(from, to, status, page, size, sort)` mirrors
      `GET /api/menus` and nothing else. It takes no `menuId`, and its
      "deepen a single match into detail" step is removed — `get_meal_plan`
      owns detail.
- [x] FR-012: `get_menu` relays the backend's paginated
      `{success, message, data: {content[], totalElements, totalPages}}`
      envelope unchanged, so the tool result always says whether more menus
      exist beyond the returned page.
- [x] FR-013: `update_menu`'s BR-MCP-005 pre-check keeps working after
      `period` is removed from the backend.

### Non-Functional Requirements

- [x] NFR-SEC-001: Every `/details` read is scoped to the JWT's user id; no
      filter combination can reach another user's menus, and a foreign
      `menuId` is a 404 rather than a 403 (BR-MENU-001).
- [x] NFR-PERF-001: `/details` loads `menu_detail` with a fetch-join on
      `dish`, so one call is a bounded number of queries rather than
      `1 + (7 × meals)` lazy loads. The same fix removes the N+1 from the
      write paths' detail assembly.
- [x] NFR-MAINT-001: Window resolution stays in `MenuQueryFilterResolver`;
      the controller only maps parameters to a filter object.
- [x] NFR-MAINT-002: `/details` and `GET /api/menus` share one resolver and
      one specification, so the two reads cannot drift on window semantics.
- [x] NFR-TEST-001: Unit tests cover both bounds being optional on `/details`
      and the default-week rule; integration tests cover `/details` by range,
      by `menuId`, and the 404 on a foreign id.

## Design overview

`MenuQueryFilterResolver` collapses from six parameters to three
(`from`, `to`, `status`) and loses the `period` switch entirely. `from`/`to`
become a plain pair of independently-optional inclusive bounds, which both
endpoints share (NFR-MAINT-002) — this is what lets `/details` default to the
current week with no separate code path.

`/details` reuses `MenuSpecifications.ownedByWithinWindowAndStatus` and adds
`MenuDetailRepository.findByMenusOrderByDateAscMealTypeAsc` returning
`List<MenuDetail>` with `dish` fetch-joined. `MenuService.listMealPlans`
groups by menu, then by date, mirroring the existing `toDetail` grouping.

Three new response records — `MenuMealPlanResponse`, `MenuMealPlanDayResponse`,
`MealDetailResponse` with a nested `DishResponse` — leave the write paths'
`MenuDetailResponse`/`MealResponse` untouched, so no merged response shape
changes.

On the MCP side `BackendClient` loses `getMenuById` and gains `getMealPlan`
pointing at `/api/menus/details`; `getMenus` keeps pointing at `/api/menus`.
`get_menu` becomes a straight relay of the paginated list and no longer
deepens a single match, and its `week`/`status`-of-`period` parameterisation
is replaced by the same flat `from`/`to` pair the backend now takes.
`get_meal_plan` is the only tool that reaches `/details`.

## Success metrics

- A chat turn asking "what's my meal plan this month?" returns real dish names
  for every matching menu in one tool call, where today it returns none when
  two or more menus match.
- `GET /api/menus/details` with no parameters returns the current week's
  detail without the caller knowing the date.
- The `from`/`to`-without-`period` 400 — the most natural LLM mistake on the
  old surface — no longer exists.

## Acceptance criteria

**As a** member asking the assistant about my meals, **I want to** get the
full day-by-day plan for any date range in one call, **so that** the answer
names real dishes instead of a list of opaque menu ids.

- [x] Given menus spanning several weeks, when I call `/details` with a range
      covering them, then every one arrives with `days[]` and `meals[]`
      populated, each meal carrying a full `dish` object.
- [x] Given no `from` and no `to`, when I call `/details`, then it returns the
      current ISO week's menus.
- [x] Given a `menuId`, when I call `/details?menuId=`, then that menu's
      detail is returned regardless of any `from`/`to` supplied alongside it.
- [x] Given another user's `menuId`, when I call `/details?menuId=`, then I
      get 404 and no data.
- [x] Given a menu whose week straddles a month boundary, when I request
      either adjacent month on `GET /api/menus?from&to`, then it appears in
      both.
- [x] Given the assistant asks about a week holding two menus, when it calls
      `get_meal_plan`, then both arrive with meals — the case that today
      returns summaries only.
- [x] Given the assistant asks which menus exist, when it calls `get_menu`,
      then it receives the paginated summary payload with `totalElements`, and
      no `menuId` parameter exists on the tool.
- [x] Given `GET /api/menus/{menuId}`, when any caller uses it, then the method
      is gone (`405 Method Not Allowed` — the path still serves `PATCH` and
      `POST .../status`) and `/details?menuId=` is the documented
      replacement.

## Risks / open questions

- **Breaking change, unreconciled.** `GET /api/menus/{menuId}` and the
  `period`/`date`/`month` selectors are merged, documented and consumed by the
  frontend. This spec removes them with no deprecation window, at the product
  owner's explicit direction. Any frontend caller must move in the same
  release.
- BR-MCP-002's "exactly four tools" becomes five. The rule is updated in the
  same change; the tool count is asserted by an existing test that must be
  amended, not deleted.
- `MenuDetailResponse` (write paths) and `MenuMealPlanResponse` (`/details`)
  are near-duplicates that will drift. Accepted for now to avoid changing
  merged write-path response shapes; worth a follow-up if a third appears.

---

## Related

- Predecessor: `docs/feats/query-menus-by-range.md` (#126)
- Business rules: `docs/brs/menus.md`, `docs/brs/mcp-tools.md`
- API references: `docs/apis/menus/get-menus.md`,
  `docs/apis/menus/get-menus-details.md`, `docs/apis/mcp/post-mcp.md`
- ADR: `docs/adrs/012-multi-module-mcp-tool-surface.md`
