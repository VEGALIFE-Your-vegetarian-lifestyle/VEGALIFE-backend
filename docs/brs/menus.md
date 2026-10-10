# Business Rules: Menus

Business rules for the weekly menu domain. These constrain every menu read
path and, where noted, the menu write paths that later issues (#17/#18/#19)
add.

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
- Written here; the write-time app-level guard is added by the create/update
  issues (#17/#18/#19).

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
