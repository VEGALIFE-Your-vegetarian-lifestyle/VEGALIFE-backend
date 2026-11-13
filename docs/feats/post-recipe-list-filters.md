# Feature Spec: Post and Recipe List Filters

## Status

Approved

## Author / owner

Vegalife backend team

## Summary

Give each post and recipe list route its own filters, and give the two public list
routes a `keyword` search that returns the entity's own response shape instead of
the mixed `GET /api/search` payload. Post and recipe list routes now cover what the
generic search does, so `GET /api/search` is deprecated.

## Problem / motivation

`GET /api/search?q=` returns one polymorphic `SearchResultItem` page mixing posts,
videos, recipes, and restaurants. A client cannot render the real post or recipe
card from it — it has to re-fetch every hit through the typed route — and each
module's "browse" endpoint (`GET /api/posts`, `GET /api/recipes`) can only page,
not filter. Browsing "vegan posts in this category" or "easy 30-minute recipes" is
impossible without the generic search.

The list routes are also inconsistent: `GET /api/posts/me` is paginated but
`GET /api/recipes/me` returns the caller's whole recipe list in one response, so
the two author-scoped lists behave differently.

## Goals

- Add per-route filters that narrow an existing, typed list without changing its
  response shape (except `GET /api/recipes/me`, below).
- Add `keyword` to the two public list routes only: `GET /api/posts` and
  `GET /api/recipes`.
- Keep every route's visibility rule intact: filters only narrow, never broaden.
- Make `GET /api/recipes/me` paginated like `GET /api/posts/me`.
- Deprecate `GET /api/search` in the API documentation and OpenAPI output, without
  removing it or changing its behavior.

## Non-goals

- Removing `GET /api/search` or changing its matching/response shape.
- Filtering `GET /api/posts/{postId}/related` or any admin listing; the admin
  listings already have their own filters.
- Sorting parameters. Every list keeps its fixed order.
- Matching recipe author names, ingredient quantities/units, or nutrition values.
- New indexes or schema changes.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/posts` accepts `keyword`, `categoryId`, `createdFrom`, and
      `createdTo` in addition to `page`/`size`. It still returns published,
      non-deleted posts ordered by `published_at DESC, created_at DESC`.
- [ ] FR-002: `GET /api/posts/me` accepts `status`, `categoryId`, `createdFrom`, and
      `createdTo`. It still returns every non-deleted post of the caller, any
      status, ordered by `created_at DESC`.
- [ ] FR-003: `GET /api/users/{userId}/posts` accepts `categoryId`, `createdFrom`,
      and `createdTo`. Visibility and ordering are unchanged: guests and other
      members see published posts only (`published_at DESC, created_at DESC`);
      the owner and Administrators see every non-deleted status
      (`created_at DESC`).
- [ ] FR-004: `GET /api/recipes` accepts `keyword`, `dishId`, `difficulty`, and
      `maxTotalTimeMinutes`. It still returns approved, non-deleted recipes
      ordered by `created_at DESC`.
- [ ] FR-005: `GET /api/recipes/me` accepts `moderationStatus`, `dishId`,
      `difficulty`, and `maxTotalTimeMinutes`, and now accepts `page`/`size`. Its
      response changes from a bare `List<RecipeResponse>` to
      `PageResponse<RecipeResponse>`, ordered by `created_at DESC`.
- [ ] FR-006: `keyword` is trimmed; blank or absent means no filter. Matching is a
      case-insensitive whole-phrase substring (no tokenization, ranking, stemming,
      or synonyms).
- [ ] FR-007: Post `keyword` matches `title` or `content` (plain text, not the
      `rawContent` JSON document).
- [ ] FR-008: Recipe `keyword` matches `name`, `description`, `instructions`, the
      attached dish's `name`, or the name of any attached ingredient. A recipe
      with several matching ingredient lines appears once.
- [ ] FR-009: `%`, `_`, and `\` in `keyword` are matched literally, not as SQL
      wildcards.
- [ ] FR-010: `categoryId` matches a post that carries that category; a post is
      never returned twice. Recipes have no category filter — a recipe has no
      category of its own, only categories reached through attached posts.
- [ ] FR-011: `status` accepts `drafted`, `processing`, `published`, and
      `unpublished`. `moderationStatus` accepts `PENDING`, `APPROVED`, and
      `REJECTED`. `difficulty` accepts `EASY`, `MEDIUM`, and `HARD`. Any other
      value is `400 Validation failed`.
- [ ] FR-012: `maxTotalTimeMinutes` matches a recipe whose
      `prepTimeMinutes + cookTimeMinutes` is at most the supplied value. A recipe
      missing either time does not match (unknown is not "short enough").
- [ ] FR-013: `createdFrom`/`createdTo` are inclusive ISO-8601 instants on
      `createdAt`.
- [ ] FR-014: Blank or absent filters never narrow. All supplied filters combine
      with AND.
- [ ] FR-015: `GET /api/search` is marked deprecated in its API reference and in
      the generated OpenAPI document; it still responds exactly as before.

### Non-Functional Requirements

- [ ] NFR-PERF-001: Filtering happens in the database query, not in memory after
      loading the page, so `page`/`size` and `totalElements` stay correct.
- [ ] NFR-SCALE-001: Every list route returns a bounded page (`size` 1–100).
      `GET /api/recipes/me` stops returning an unbounded list.
- [ ] NFR-MAINT-001: The keyword predicate lives in one place per entity
      (`PostSpecifications`, `RecipeSpecifications`) and is reused rather than
      re-implemented in each service method.
- [ ] NFR-SEC-001: A filter never widens visibility. Post filters are always
      combined with the endpoint's existing status predicate; recipe filters are
      always combined with `moderationStatus = APPROVED` on the public route.
- [ ] NFR-COMPAT-001: The `GET /api/recipes/me` envelope change is a documented
      breaking change; every other response shape is unchanged.

## Design overview

Each route gets a request DTO carrying exactly the filters it supports, so no
endpoint silently accepts an ignored parameter:

| Route | Request DTO | Filters |
|---|---|---|
| `GET /api/posts` | `PostFeedRequest` | `keyword`, `categoryId`, `createdFrom`, `createdTo` |
| `GET /api/posts/me` | `MyPostListRequest` | `status`, `categoryId`, `createdFrom`, `createdTo` |
| `GET /api/users/{userId}/posts` | `UserPostListRequest` | `categoryId`, `createdFrom`, `createdTo` |
| `GET /api/recipes` | `RecipeListRequest` (extended) | `keyword`, `dishId`, `difficulty`, `maxTotalTimeMinutes` |
| `GET /api/recipes/me` | `MyRecipeListRequest` (new) | `moderationStatus`, `dishId`, `difficulty`, `maxTotalTimeMinutes`, `page`, `size` |

`PostSpecifications` grows one filtering specification that composes the existing
status/author/category/date predicates with an optional keyword predicate;
`PostSpecifications.searchable` keeps its published-only behavior but reuses that
keyword predicate. `RecipeSpecifications` gains a catalogue specification that
composes author/moderation-status/dish/difficulty/time/keyword predicates, and the
new keyword predicate includes the dish and an `EXISTS` subquery over
`recipe_ingredient` → `ingredient`, so ingredient matches never duplicate a recipe
row.

Both services switch from derived repository queries to
`JpaSpecificationExecutor.findAll(spec, pageable)` with an explicit `Sort`, which
keeps the existing order and lets the filter run in SQL. No schema change is
needed: every filter is already a column on `post` or `recipe`, or a reachable
association.

`SearchController.search` is annotated with `@Operation(deprecated = true)`; its
API reference gets a deprecation banner pointing at the typed filters.

## Success metrics

Before merge, the integration suite covers, for each route: every filter matching,
a filter excluding a non-matching row, filters combining with AND, blank keyword
ignored, case-insensitive keyword, wildcard characters treated literally, and the
route's visibility rule still holding. `GET /api/recipes/me` additionally covers
the page envelope. No production metric is available yet.

## Acceptance criteria

**As a** visitor, **I want** to browse posts and recipes with the filters that
matter to that entity, **so that** I get typed results without a second fetch;
**as a** member, **I want** my own posts and recipes to behave the same way,
**so that** the API is predictable.

- [ ] Given a guest, when `GET /api/posts?categoryId=<id>` is called, then only
      published posts in that category are returned.
- [ ] Given a guest, when `GET /api/posts?keyword=pho` is called, then only
      published posts whose title or content contains "pho" (any case) are
      returned.
- [ ] Given a member, when `GET /api/posts/me?status=drafted` is called, then only
      the caller's non-deleted drafts are returned.
- [ ] Given a member, when `GET /api/recipes/me?moderationStatus=REJECTED` is
      called, then only their rejected, non-deleted recipes are returned as a page.
- [ ] Given a guest, when `GET /api/recipes?difficulty=EASY&maxTotalTimeMinutes=30`
      is called, then only approved recipes that satisfy both are returned.
- [ ] Given a guest, when `GET /api/recipes?keyword=tofu` is called, then approved
      recipes whose text, dish name, or an ingredient name contains "tofu" are
      returned, each at most once.
- [ ] Given any caller, when a filter value is not a documented enum value, then
      the response is `400 Validation failed`.
- [ ] Given a guest, when `GET /api/recipes?keyword=%` is called, then a literal
      `%` is searched for, not every recipe.

## Risks / open questions

- `GET /api/recipes/me` changes response shape (`data` array → page envelope).
  Clients must adapt in the same release; this is the only breaking change.
- Leading-wildcard `LIKE` cannot use a B-tree index, so a keyword request scans
  the visible rows, as `GET /api/search` already does. Pages stay bounded, but a
  very large catalogue will need a trigram/full-text index later.
- Recipe keyword matching reaches `dish` and `recipe_ingredient` → `ingredient`.
  The `EXISTS` subquery keeps pagination counts correct; the extra join cost is
  paid only when `keyword` is supplied.
- `GET /api/search` stays functional while deprecated, so its narrower recipe
  matching (text fields only) temporarily differs from the recipe list keyword.
  Its documentation says so and points at the typed route.
