# Feature Spec: Admin Post List Is a Superset of the Member Post List

## Status

Approved

## Author / owner

Backend team.

## Summary

`GET /api/admin/posts` currently returns 15 of the 16 fields a member-facing post listing carries. This feature makes the admin list item a strict superset of `PostListResponse` — every member field present, plus the moderation fields only an admin may see — so one client-side post shape works for both surfaces and a reviewer never has to fetch the member view to see what a member sees.

## Problem / motivation

`AdminPostListResponse` (`src/main/java/com/vegalife/dto/response/admin/AdminPostListResponse.java`) and `PostListResponse` (`src/main/java/com/vegalife/dto/response/post/PostListResponse.java`) are two hand-written DTOs over the same `Post` entity. Comparing them field by field, the admin item is missing exactly three member fields:

| Field | `PostListResponse` | `AdminPostListResponse` | Why it is missing today |
|---|---|---|---|
| `avatarUrl` | yes | no | The avatar lives on `UserProfile`, not `User`, so `AdminPostMapper` cannot reach it; only `PostService.applyAuthorAvatars` resolves it. |
| `rawContent` | yes | no | Never declared on the admin DTO, though `Post.rawContent` is already mapped by name on `AdminPostMapper`. |
| `recipeIds` | yes | no | Never declared; the feed also leaves it empty, and only the single-post route populates it. |

The remaining 13 fields (`id`, `userId`, `displayName`, `title`, `content`, `featuredImageUrl`, `categoryIds`, `mediaIds`, `status`, `rejectionReason`, `viewCount`, `publishedAt`, `createdAt`) are already on both. The admin item additionally carries `flag`, `flagDetail`, and `email`.

The practical cost: the two payloads drift silently. Nothing in the build fails when a field is added to `PostListResponse` and forgotten on `AdminPostListResponse` — that is how these three went missing. An admin client also cannot render a post card identically to the member feed without a second request per item, and an admin reviewing a rich-text post cannot see the editor document (`rawContent`) the author actually submitted.

## Goals

- The admin list item contains every field that `PostListResponse` contains, under the same name and with the same meaning.
- The admin list item keeps its admin-only fields (`flag`, `flagDetail`, `email`).
- A regression that drops a member field from the admin DTO fails the build rather than shipping silently.
- An admin list page resolves avatars and recipe ids without an N+1 query per row.

## Non-goals

- Changing `GET /api/posts`, `GET /api/posts/{postId}`, `GET /api/posts/me`, `GET /api/users/{userId}/posts`, or the saved-items post shape. Only the admin list changes.
- Making the admin list *equal* to the member list. The admin fields stay; this is superset, not parity.
- Populating `recipeIds` on the member feed or the member list routes. Those stay empty there by design (`PostService.readPublishedPostsByIds`, `PostService.listFeed`), and this feature does not touch them.
- Making `AdminPostDetailResponse` (`GET /api/admin/posts/{postId}`) a superset of the list item. Its Javadoc claims that today and is wrong; correcting it is a documentation fix noted under Risks, not part of the payload change.
- Adding a `keyword` filter to `GET /api/admin/posts`, or fixing its fan-out query cost. Both are separate concerns.

## Requirements

### Functional Requirements

- [ ] FR-001: `AdminPostListResponse` declares `rawContent` (`JsonNode`), populated from `Post.rawContent` — the same value the member list returns.
- [ ] FR-002: `AdminPostListResponse` declares `avatarUrl` (`String`), resolved from the author's `UserProfile.avatarUrl` in one batched query per page, mirroring `PostService.applyAuthorAvatars`. Null when the author has no profile row (BR-PROFILE-003).
- [ ] FR-003: `AdminPostListResponse` declares `recipeIds` (`Set<UUID>`), populated with the post's attached, non-deleted recipe ids in one batched query per page. Empty set when the post has no recipes.
- [ ] FR-004: Every one of the 16 `PostListResponse` field names exists on `AdminPostListResponse` with the same JSON name.
- [ ] FR-005: `flag`, `flagDetail`, and `email` remain on `AdminPostListResponse` and keep their current values.
- [ ] FR-006: An integration test asserts that the field-name set of the admin list item is a superset of the field-name set of the member feed item, computed from the two live JSON responses rather than a hardcoded list.
- [ ] FR-007: An integration test asserts the three new fields carry real values: a member with a profile row shows that `avatarUrl`, a post with an attached recipe lists its id in `recipeIds`, and an editor post returns its `rawContent` object.
- [ ] FR-008: `recipeIds` excludes soft-deleted recipes, matching the member single-post behaviour.

### Non-Functional Requirements

- [ ] NFR-SCALE-001: Returning a page of 100 admin posts issues at most one avatar query and one recipe-id query for the page, independent of page size — no per-row lookup.
- [ ] NFR-SEC-001: No new field exposes anything to a non-admin caller. `avatarUrl` and `rawContent` are already on member-facing responses; `flag`, `flagDetail`, and `email` remain confined to the admin DTO. The endpoint stays behind `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] NFR-MAINT-001: The superset invariant is enforced by a test that reads both payloads, so a future field added to `PostListResponse` alone fails CI with a message naming the missing field.
- [ ] NFR-PERF-001: The added page cost stays at two extra queries regardless of `size`; no change to the existing specification-based count and fetch.

## Design overview

The admin DTO stays a flat, hand-declared class — the codebase has no DTO inheritance anywhere, and `AGENTS.md` prefers composition over inheritance — so the three fields are declared explicitly rather than pulled in by `extends`. The invariant is then held by FR-006's live-payload test instead of by the compiler.

`AdminPostService` gains the two batch lookups that `AdminPostMapper` cannot perform, because MapStruct maps one `Post` at a time and both lookups need the whole page:

1. **Avatars** — `UserProfileRepository.findByUserIdIn` already exists and is what `PostService` uses. The service collects the page's author ids, resolves them in one call, and assigns `avatarUrl` on the mapped responses, exactly as `PostService.applyAuthorAvatars` does.
2. **Recipe ids** — a projection query on `RecipeRepository` over the `Recipe.posts` join table, returning `(postId, recipeId)` pairs for the page's post ids and filtering `deletedAt IS NULL`. The service groups them by post id and assigns `recipeIds` per response. Querying the join table directly avoids touching the lazy `Recipe.posts` collection per row.

`rawContent` needs no lookup: `AdminPostMapper` already maps it by name from `Post`, so declaring the DTO field is sufficient. `AdminPostService.listPosts` currently maps via `page.map(adminPostMapper::toResponse)`; it switches to mapping the page content into a list and enriches it before building the `PageResponse`, the same shape `PostService.toPage` uses.

## Success metrics

- `GET /api/admin/posts` returns 16 member field names plus 3 admin field names, verified by FR-006's test, from the first merged commit.
- No more than two additional queries per admin list request, confirmed by the NFR-SCALE-001 test.

## Acceptance criteria

**As an** administrator reviewing the moderation queue, **I want** each post in `GET /api/admin/posts` to carry every field the member feed shows plus the moderation fields, **so that** I can inspect a post completely in one request and render it the way its author and readers see it.

- [ ] Given an admin token and a published post with an author who has a profile row, when listing admin posts, then the item contains that author's `avatarUrl`.
- [ ] Given a post with two attached non-deleted recipes and one soft-deleted recipe, when listing admin posts, then `recipeIds` contains the two live ids and not the deleted one.
- [ ] Given an editor post with a `rawContent` document, when listing admin posts, then the item's `rawContent` equals that document.
- [ ] Given the member feed response and the admin list response for the same page, when their item field-name sets are compared, then every member field name appears in the admin item.
- [ ] Given a post held for review, when listing admin posts, then `flag` and `flagDetail` are still present alongside the member fields.
- [ ] Given a non-admin token, when calling the endpoint, then it still returns 403.

## Risks / open questions

- **Duplicate field declarations can still drift.** The user-confirmed trade-off is a flat duplicate plus a live-payload test (FR-006) rather than DTO inheritance. A field added to `PostListResponse` on a branch where the admin test is not run locally will be caught in CI, not at compile time. Accepted deliberately to keep DTO style uniform.
- **`AdminPostDetailResponse`'s Javadoc is already inaccurate** — it says it "carries everything `AdminPostListResponse` does, plus …" while dropping `featuredImageUrl`, `categoryIds`, `mediaIds`, `viewCount`, `publishedAt`, and `createdAt`. That wording is corrected as part of the documentation update here, but making the detail payload an actual superset is out of scope.
- **`rawContent` on a list page enlarges the response.** It is a full editor document per row. The member feed already accepts that cost, and `size` is capped at 100, so this is accepted; if payload size becomes a problem the fix belongs on both surfaces at once.
- **Sorting/filtering are unchanged.** The recipe-id lookup joins the post ids already fetched, so it cannot widen or reorder the result set.
