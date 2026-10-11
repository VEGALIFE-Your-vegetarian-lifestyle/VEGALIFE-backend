# Business Rules: Menus

Business rules for the weekly menu domain. These constrain the menu read
paths and the create/update paths; rules for the status transitions land
with the issue that adds them (#19).

---

# Business Rule: Menu Ownership Comes from Authentication

## Rule ID
`BR-MENU-001`

## Status
Active

## Statement
A menu is readable only by the user whose ID is carried in the caller's JWT.
No read accepts a user ID from a request parameter. A menu belonging to
another user is indistinguishable from a non-existent one.

## Rationale
Menus are private personal data; a member must never be able to enumerate or
read another member's meal plans, and existence must not leak.

## Scope & Exceptions
Applies to every menu read (list and detail). There is no admin or public
menu read in this scope.

## Enforcement
- `MenuService` derives the owner from the authenticated principal and always
  scopes queries by `user_id`; `GET /api/menus/{menuId}` returns 404 for a
  foreign or absent id (never 403).
- API: 404 `Menu not found`.

## Last Reviewed
2026-10-10, by zuyzz (backend)

---

# Business Rule: Menu Read Windows Are Intersections

## Rule ID
`BR-MENU-002`

## Status
Active

## Statement
A menu is included in a filtered result when its `[start_date, end_date]`
window intersects the requested window, inclusive of touching endpoints. The
predicate is `menu.start_date <= window.to AND menu.end_date >= window.from`.
A menu whose window only touches the requested window at a boundary (e.g. a
menu ending on the day the requested range starts) is included; a menu with a
one-day gap is excluded.

## Rationale
Members think in weeks and months; a plan that straddles a boundary should
appear under both adjacent periods, and a plan that ends exactly where a range
begins still belongs to that range.

## Scope & Exceptions
Applies to `period=week`, `period=month`, and `period=custom`. With no
`period`, the window is unbounded and every own menu is returned.

## Enforcement
- `MenuRepository` intersection query with each bound applied only when
  non-null; unit tests cover the touching and gap boundary cases.

## Last Reviewed
2026-10-10, by zuyzz (backend)

---

# Business Rule: One Scheduled Week per User, No Overlap

## Rule ID
`BR-MENU-003`

## Status
Active

## Statement
A user may have at most one `scheduled` menu covering any given date. Two
`scheduled` menus for the same user must not have overlapping
`[start_date, end_date]` windows. `drafted`, `cancelled`, and `completed`
menus are not constrained.

## Rationale
A member has exactly one meal plan in effect for any week; overlapping
scheduled plans are contradictory and would make "what's planned this week?"
ambiguous.

## Scope & Exceptions
Applies to `scheduled` rows only. A `completed` or `cancelled` row may overlap
anything, and drafts are unrestricted.

## Enforcement
- Database: `menu` exclusion constraint `uq_menu_scheduled_no_overlap`
  (`EXCLUDE USING gist (user_id WITH =, daterange(start_date, end_date, '[]')
  WITH &&) WHERE (status = 'scheduled')`) added in
  `V30__menu_servings_preferences_and_scheduled_guard.sql`.
- Written here; the write-time app-level guard is added by the update/status
  issues (#18/#19). The create path (#17) only ever produces `drafted` menus,
  which this rule does not constrain.

## Last Reviewed
2026-10-10, by zuyzz (backend)

---

# Business Rule: Menu Windows Are Evaluated in Server (UTC) Time

## Rule ID
`BR-MENU-004`

## Status
Active

## Statement
`period=week` resolves to the Monday–Sunday ISO week and `period=month` to the
first/last calendar day of the requested month, both computed in server (UTC)
time. The default for `date` is the current server date and for `month` the
current server month.

## Rationale
There is no per-member timezone stored yet; a single, stated clock keeps
results deterministic and testable.

## Scope & Exceptions
Applies to `period=week` and `period=month` defaulting and resolution. Revisit
if per-member timezones are introduced.

## Enforcement
- `MenuQueryFilterResolver` resolves windows using `LocalDate.now(ZoneOffset.UTC)`
  and ISO week rules.

## Last Reviewed
2026-10-10, by zuyzz (backend)

---

# Business Rule: New Menus Are Drafts and Must Cover a Valid, Varied Week

## Rule ID
`BR-MENU-005`

## Status
Active

## Statement
A menu created through `POST /api/menus` always lands in status `drafted`;
scheduling is a separate, explicit transition. The owner is the authenticated
caller (never a request field), and `end_date` is derived as
`start_date + 6` (never client-supplied). The submitted `days` must satisfy
all of:

- exactly the 7 dates `start_date .. start_date + 6`, each appearing once;
- each date carries 1–3 meals, at most one per `meal_type`
  (`BREAKFAST`, `LUNCH`, `DINNER`);
- every meal's `servings` is an integer ≥ 1;
- every referenced dish exists and is not soft-deleted;
- no dish appears more than 3 times in the menu;
- the menu references at least 5 distinct dishes.

## Rationale
Menus are generated by the LLM on behalf of a member, so the API is the last
line of defence for structural sanity: a "week" that is missing days,
duplicated, or built on a single dish is not a usable meal plan. Variety
(repeat/distinct counts) is the structural proxy for the issue's "meal
variety / nutrition balance" intent — `dish` carries no nutrition columns, so
nutritional balance cannot be enforced server-side and remains the LLM's job.

## Scope & Exceptions
Applies to the create path only. Drafts are intentionally unconstrained by the
overlap rule (BR-MENU-003), so multiple drafts for the same week are allowed.
Thresholds (`≤ 3` repeats, `≥ 5` distinct) are service constants and may be
tuned without breaking this rule's shape.

## Enforcement
- `MenuValidator` validates the payload before persistence;
  `MenuService.createMenu` persists menu + days in one transaction.
- API: 400 with a message naming the violated rule; nothing is persisted when
  validation fails.

## Last Reviewed
2026-10-12, by zuyzz (backend)

---

# Business Rule: Menus Are Editable Only While Drafted or Scheduled

## Rule ID
`BR-MENU-006`

## Status
Active

## Statement
`PATCH /api/menus/{menuId}` may update a `drafted` or a `scheduled` menu.
A `cancelled` or `completed` menu is never editable and returns 409. An
update replaces the menu's meals wholesale (the new `days` must form a
complete, valid week under BR-MENU-005 — there is no partial meal edit)
and may change `dietaryPreferences` and `notes`. The menu's window
(`startDate`/`endDate`) is immutable, so editing can never introduce a
scheduled-overlap violation (BR-MENU-003).

## Rationale
A member finalising or having finalised a plan (cancelled/completed)
must not have it silently rewritten; drafts and scheduled plans are the
states where adjusting meals is expected behaviour. Re-validating the
whole week keeps the invariant simple: any stored menu is a complete,
valid week.

## Scope & Exceptions
Applies to the update path. The window is never editable, so the
overlap constraint is unaffected by updates; V30's exclusion constraint
remains the database backstop. The "current week only" restriction from
the issue is enforced at the MCP tool layer, not here — the REST API
stays general.

## Enforcement
- `MenuService.updateMenu` loads the menu by id **and** owner, rejects
  non-editable statuses, re-validates via `MenuValidator`, and replaces
  the days in one transaction.
- API: 404 for a foreign/absent id, 409 for `cancelled`/`completed`, 400
  for an invalid resulting week.

## Last Reviewed
2026-10-12, by zuyzz (backend)
