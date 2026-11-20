# Feature Spec: Admin List Filter Parity and Search

## Status

Approved

## Author / owner

Backend team.

## Summary

Give the three admin list endpoints (`GET /api/admin/users`, `GET /api/admin/posts`, `GET /api/admin/recipes`) the full set of filters their own response fields imply, and add a `keyword` text search to all three so an administrator can find a record instead of paging through it.

## Problem / motivation

Every admin list today supports paging, sorting and a createdAt range, but none supports text search, and each is missing filters for values it already returns:

| Endpoint | Missing today |
|---|---|
| `GET /api/admin/users` | no `keyword`; no `emailVerified` filter, although the column exists and the dashboard counts it |
| `GET /api/admin/posts` | no `keyword`, although the member feed has it |
| `GET /api/admin/recipes` | no `keyword`; no `dishId`, `difficulty` or `maxTotalTimeMinutes`, although the member catalogue has all three; no way to isolate soft-deleted rows even though every item returns a derived `status` of `ACTIVE`/`DELETED` |

The practical cost is a support workflow with no query support anywhere in the API: locating an account by email, or a post by title, requires paging the whole table. `GET /api/search` does not help — it covers posts, videos, recipes and restaurants, never users, and it returns a mixed payload rather than the admin item shape.

`GET /api/admin/users` also accepts any sort property with no allowlist, so a typo reaches Spring Data's `PropertyReferenceException`, which has no handler in `GlobalExceptionHandler` and surfaces as a 500 instead of the 400 the other two admin lists return.

## Goals

- Each admin list accepts a `keyword` filter that searches the fields an administrator would identify a record by.
- Each admin list can filter on every value its own item shape exposes, where that value is a stored column.
- Every invalid filter or sort value returns `400 Validation failed`, never `500`.
- The three lists document the same parameter conventions for the parameters they share.

## Non-goals

- Changing any member-facing list route, or making the admin lists a superset of the member responses (already settled for posts; out of scope here).
- Semantic or embedding-based search — `keyword` stays a case-insensitive substring match, matching the member lists.
- Filtering by values that are derived and not stored, such as the post `flagDetail` or recipe nutrition totals.
- Full-text indexes or new database columns; no migration.
- Reinstating `GET /api/search` for users.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/admin/users` accepts `keyword`, matching case-insensitively on `displayName` or `email` as a whole-phrase substring.
- [ ] FR-002: `GET /api/admin/users` accepts `emailVerified` (`true`/`false`), narrowing to accounts whose `emailVerified` equals that value.
- [ ] FR-003: `GET /api/admin/posts` accepts `keyword`, matching case-insensitively on `title` or `content` (plain text, not `rawContent`).
- [ ] FR-004: `GET /api/admin/recipes` accepts `keyword`, matching case-insensitively on `name`, `description`, `instructions`, the attached dish's `name`, or any attached ingredient's `name`. A recipe matching several ingredient lines is returned once.
- [ ] FR-005: `GET /api/admin/recipes` accepts `dishId`, `difficulty` (`EASY`/`MEDIUM`/`HARD`) and `maxTotalTimeMinutes`, matching the member catalogue's semantics: `maxTotalTimeMinutes` matches when `prepTimeMinutes + cookTimeMinutes` is at most the value, and a recipe missing either time does not match.
- [ ] FR-006: `GET /api/admin/recipes` accepts `status` (`ACTIVE`/`DELETED`), isolating soft-deleted rows or excluding them; absent means both, preserving today's behaviour.
- [ ] FR-007: `%`, `_` and `\` in `keyword` are matched literally, not as SQL wildcards, on all three lists.
- [ ] FR-008: A blank or absent `keyword` never narrows the result. Every supplied filter combines with AND and only narrows — no filter widens visibility.
- [ ] FR-009: `GET /api/admin/users` validates `sort` against an allowlist and rejects an unknown property with `400 Validation failed` naming the allowed properties.
- [ ] FR-010: `GET /api/admin/users` accepts the sort properties `createdAt`, `updatedAt`, `displayName`, `email`, `status`, `role`; `username` stays accepted as an alias for `displayName`.
- [ ] FR-011: A `keyword` or filter that matches nothing returns `200` with an empty `content` array, never `404`.
- [ ] FR-012: `createdFrom` after `createdTo` is rejected with `400` on all three lists.

### Non-Functional Requirements

- [ ] NFR-SCALE-001: Filtering runs in the database query via the existing `Specification` classes, so `page`, `size` and `totalElements` stay correct; no in-memory filtering after the page is loaded.
- [ ] NFR-MAINT-001: The `LIKE` escape rule lives in one place, shared by every specification that implements a keyword filter, rather than being re-implemented per entity.
- [ ] NFR-MAINT-002: The recipe keyword predicate lives in `RecipeSpecifications` and is reused by both the member catalogue and the admin list, not duplicated.
- [ ] NFR-SEC-001: No filter or sort property exposes a field absent from the endpoint's response shape; `passwordHash` is never sortable.
- [ ] NFR-SEC-002: All three endpoints remain behind `@PreAuthorize("hasRole('ADMIN')")`; no new path rule is added.
- [ ] NFR-COMPAT-001: Every existing parameter keeps its name, type and meaning; existing requests return the same result set.

## Design overview

Each admin list already has a request DTO carrying exactly the filters it accepts, so the change is additive per DTO:

| Route | Request DTO | Added |
|---|---|---|
| `GET /api/admin/users` | `admin/UserListRequest` | `keyword`, `emailVerified`; sort allowlist enforced in `AdminService` |
| `GET /api/admin/posts` | `admin/PostListRequest` | `keyword` |
| `GET /api/admin/recipes` | `admin/RecipeListRequest` | `keyword`, `dishId`, `difficulty`, `maxTotalTimeMinutes`, `status` |

`PostSpecifications.allWithFilters` gains a `keyword` argument and delegates to its existing `filtering(...)` method, which already implements the escaped keyword predicate — the member feed calls that method today, so the admin list reuses the identical matching rule instead of a second implementation.

`RecipeSpecifications` gains one full-parameter `allWithFilters`, which composes the same private predicate helpers the member `catalogue` already uses. `catalogue` then delegates to it with the soft-delete predicate pinned, so the recipe keyword (text + dish name + ingredient `EXISTS` subquery) and the time/difficulty predicates are written once and shared by both audiences.

`UserSpecifications.activeWithFilters` gains `keyword` and `emailVerified`; it gains no shared predicate with the other entities because user search spans different columns.

The `LIKE` escape rule currently duplicated in `PostSpecifications` and `RecipeSpecifications` moves into one small helper in the `repository` root package, which is the same rank as the domain specification packages that import it (ADR-018).

`AdminRecipeService.parseDifficulty` currently dereferences its argument without a null check; it is corrected as part of adding the filter, otherwise an omitted `difficulty` would throw a `NullPointerException` → 500.

## Success metrics

- Every filter listed in FR-001…FR-012 has an integration test that both matches and excludes a row.
- Zero admin list parameters are undocumented in `docs/apis/admin/`, including `moderationStatus` on recipes, which the code has supported but the reference has never listed.

## Acceptance criteria

**As an** administrator, **I want** to search and filter the user, post and recipe lists the same way I browse members' content, **so that** I can find one record without paging through the platform.

- [ ] Given an admin token and an account registered as `jane@example.com`, when requesting `/api/admin/users?keyword=jane`, then that account is returned and a differently-named one is not.
- [ ] Given unverified accounts, when requesting `/api/admin/users?emailVerified=false`, then only unverified accounts are returned.
- [ ] Given a post titled `Vegan pho`, when requesting `/api/admin/posts?keyword=PHO`, then the post is returned regardless of its status.
- [ ] Given an approved recipe whose dish is `Pho`, when requesting `/api/admin/recipes?keyword=pho`, then it is returned once.
- [ ] Given soft-deleted recipes, when requesting `/api/admin/recipes?status=DELETED`, then only the deleted ones are returned; omitting `status` returns both.
- [ ] Given an admin token, when requesting `/api/admin/users?sort=passwordHash,asc`, then `400 Validation failed` is returned, not `500`.
- [ ] Given `keyword=%`, when listing posts, then a literal `%` is searched for rather than every post.

## Risks / open questions

- **Leading-wildcard cost.** `keyword` uses `LIKE '%…%'`, which cannot use a B-tree index, so a keyword request scans the visible rows — the same accepted cost the member lists already carry. Admin tables are the largest ones, so this is the first place a trigram index would matter; out of scope here, and recorded rather than silently accepted.
- **User keyword spans `email`.** An admin already receives `email` on every item, so searching by it exposes nothing new; a member cannot reach this endpoint at all.
- **`status` vs `moderationStatus` on recipes.** Two similarly-named filters now exist on one endpoint. `status` is deletion (`ACTIVE`/`DELETED`), `moderationStatus` is review (`PENDING`/`APPROVED`/`REJECTED`), matching the two fields the item already returns. Documented explicitly to avoid a client swapping them.
