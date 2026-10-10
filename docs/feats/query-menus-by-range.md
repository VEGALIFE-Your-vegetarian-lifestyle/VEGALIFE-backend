# Feature Spec: Query Menus by Week, Month, or Date Range

## Status

Approved

## Author / owner

zuyzz (issue #126), direction confirmed with the project owner 2026-10-10.

## Summary

A read-only API that lets an authenticated member browse their weekly meal
plans through time-shaped filters — a single ISO week, a single calendar
month, or an explicit `from`/`to` date range — instead of only by an exact
`start_date`, plus a detail endpoint that returns one plan with its days and
meals.

## Problem / motivation

Issue #126: the menu tables (`menu`, `menu_detail`) exist since V9 but no
menu Java code or endpoint exists, so the feature is unimplementable today.
Once a list endpoint exists it must answer the questions members actually
ask — "show me this month's plans", "what did I eat last week?" — without the
client issuing one request per week and stitching results together. An
exact-`start_date` filter forces that client-side pagination and is slow.

This branch also seeds the menu domain foundation (entities, enums,
repositories) that the create/update/status plans (#17/#18/#19) build on, so
it is the natural first landing for the menu feature family.

## Goals

- One endpoint returns every menu whose `[start_date, end_date]` window
  intersects a requested time window.
- Filters map to how members think about time: `week` (with a `date`), `month`
  (with a `month`), or `custom` (with `from`/`to`), plus an optional `status`.
- A detail endpoint returns one owned plan with its days and meals.
- Results are always caller-scoped, paginated, and use the shared response
  envelope, so the AI assistant's MCP `get_menu` tool can back richer queries
  with the same surface.

## Non-goals

- Per-meal / per-dish search (e.g. "when did I last eat pho").
- Cross-user or public menu browsing — results stay scoped to the caller.
- Any write behavior: creating, editing, scheduling, or status changes
  (owned by #17/#18/#19).
- Aggregation/statistics (calorie totals, nutrition summaries) — no nutrition
  data exists in the `dish` schema.
- The MCP `get_menu` tool surface itself; that is tracked in the MCP work.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/menus` supports a `period` selector with values
      `week`, `month`, or `custom` (default: absent, i.e. no time filter).
- [ ] FR-002: When `period=week`, a `date` query param (default = today)
      returns menus intersecting that ISO week (Monday–Sunday).
- [ ] FR-003: When `period=month`, a `month` query param in `YYYY-MM` format
      (default = current month) returns menus intersecting that calendar
      month.
- [ ] FR-004: When `period=custom`, `from` and `to` (both `YYYY-MM-DD`,
      inclusive) are required; `from` must be ≤ `to`; a range longer than 366
      days is rejected with 400.
- [ ] FR-005: A menu is included when its `[start_date, end_date]` window
      intersects the requested window
      (`menu.start_date <= window.to AND menu.end_date >= window.from`); any
      overlap counts, not only exact containment.
- [ ] FR-006: Results are always scoped to the authenticated user (identity
      from the JWT; never a request param).
- [ ] FR-007: Optional `status` filter accepts
      `drafted | scheduled | cancelled | completed` and combines with the time
      filter (AND).
- [ ] FR-008: Results are paginated (`page`, `size`, `sort`), ordered by
      `start_date` descending by default, and reuse the shared
      `{success, message, data}` envelope and `PageResponse` shape.
- [ ] FR-009: Conflicting or insufficient params (`period=custom` without
      `from`/`to`, `period=week` with a malformed `date`, `period=month` with
      a malformed `month`, unknown `period`, `from > to`) return 400 with a
      clear message. If `from`/`to` are supplied without `period=custom` they
      are rejected with 400 rather than silently applied.
- [ ] FR-010: `GET /api/menus/{menuId}` returns one owned plan with its days
      and meals; a missing or foreign id returns 404 (never 403, to avoid
      leaking existence).

### Non-Functional Requirements

- [ ] NFR-SEC-001: A caller can never see another user's menus through any
      filter combination; foreign data must not leak via counts or pagination
      totals.
- [ ] NFR-PERF-001: The intersection query uses the `(user_id, start_date)`
      index and stays under ~200ms p95 for a user with a few hundred menus.
- [ ] NFR-MAINT-001: Filter logic lives in the menu service layer; the
      controller only maps query params to a filter object.
- [ ] NFR-TEST-001: Unit tests cover each `period` mode and the overlap
      boundary cases (touching-but-not-overlapping windows), plus a MockMvc
      integration test per period.

## Design overview

- Two entities map the existing tables: `Menu` (`menu`) and `MenuDetail`
  (`menu_detail`), with `MenuStatus` and `MealType` enums. `end_date` is
  `start_date + 6` (a 7-day week), so intersection reduces to
  `menu.start_date <= to AND menu.end_date >= from`.
- A `MenuQueryFilter` value object carries the resolved inclusive window
  (nullable bounds = unbounded) and optional status; a resolver turns query
  params into it and raises `InvalidMenuFilterException` (400) on any invalid
  combination.
- `MenuService` runs a paginated intersection query for the list and an
  owner-scoped lookup for the detail; the controller is a thin mapping layer
  and DTOs never expose entities.
- A `btree_gist` exclusion constraint prevents overlapping `scheduled` weeks
  per user at the database level; the decision and its fallback are recorded
  in the ADR below.
- Timezone: windows are evaluated in server time (UTC). Revisit if members
  span timezones.

## Success metrics

- Every acceptance criterion below passes in the automated test suite.
- The list query uses the `(user_id, start_date)` index under `EXPLAIN` for a
  representative user with a few hundred menus.
- The MCP `get_menu` tool can answer "what's planned next month?" with one
  call against this endpoint.

## Acceptance criteria

**As a** member with several weekly plans, **I want to** browse my plans by
week, month, or a date range, **so that** I can find a past or upcoming plan
without knowing its exact start date.

- [ ] Given I have menus in the current month, when I request `period=month`,
      then I receive all of them together with their details.
- [ ] Given I have a menu whose week straddles a month boundary, when I
      request either adjacent month, then that menu appears in both.
- [ ] Given I request `period=custom` with `from` > `to`, when the request is
      sent, then I get 400 and no data.
- [ ] Given I am authenticated as user A, when I query any period, then I
      never receive user B's menus or a count that includes them.
- [ ] Given I filter by `status=scheduled` and `period=week`, when both apply,
      then only scheduled menus intersecting that week are returned.
- [ ] Given a menu id that belongs to another user, when I request its detail,
      then I get 404, not 403.

## Risks / open questions

- Month-number filtering (`month=YYYY-MM`) is offered for ergonomics alongside
  `from`/`to`; confirmed as in-scope for this issue.
- Timezone: weeks/months are resolved in server (UTC) time; revisit if members
  span timezones.
- `btree_gist` availability on the production Postgres (Render, Postgres 16)
  must be confirmed; if the extension cannot be created the app-level
  intersection check + 409 is the documented fallback (see the ADR).
