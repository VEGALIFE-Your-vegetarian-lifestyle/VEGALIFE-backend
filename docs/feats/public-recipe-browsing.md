# Feature Spec: Public Recipe Browsing and Post Route Alignment

## Status

Approved

## Author / owner

Vegalife backend team

## Summary

Make approved recipes a first-class public read surface — a paginated list at `GET /api/recipes` and a detail at `GET /api/recipes/{recipeId}` that no longer requires authorship — and align the post read routes so the public feed is `GET /api/posts` and a member's own posts are `GET /api/posts/me`.

## Problem / motivation

Recipes can only be created (`POST /api/recipes`) and read back by their author (`GET /api/recipes/me`, `GET /api/recipes/{recipeId}`). An approved recipe is therefore invisible to every other member and to guests, which contradicts its purpose: it is community content that posts attach to and that search already returns. The only cross-user recipe list today is the admin surface at `GET /api/admin/recipes`, so a member-facing recipe catalogue cannot be built at all.

The post routes are also inconsistent with the recipe ones. The public feed lives at `GET /api/posts/feed` while `GET /api/posts` means "my own posts", although `GET /api/recipes/me` has established the pattern that `me` denotes the caller-scoped list. Clients have to special-case the post module.

## Goals

- Let anyone, including guests, browse approved recipes and open one by id.
- Keep the moderation guarantee intact: a `PENDING` or `REJECTED` recipe is never readable by someone other than its author.
- Make the public feed `GET /api/posts` and the caller's own list `GET /api/posts/me`, matching `GET /api/recipes/me`.
- Keep the response shapes consistent with the existing post and recipe read models.

## Non-goals

- Recipe search/filter parameters (dish, ingredient, nutrition) on `GET /api/recipes`; search remains `GET /api/search`.
- Pagination for `GET /api/recipes/me` or changes to the recipe write surface.
- Editing or deleting recipes as a member.
- Keeping `GET /api/posts/feed` as a compatibility alias — the old path is removed.
- Authoring recipe `rejectionReason` visible to non-authors; it stays author-only.

## Requirements

### Functional Requirements

- [ ] FR-001: `GET /api/posts` returns the public feed: published, non-deleted posts, newest first, paginated by `page` (default `0`) and `size` (default `20`, `1`–`100`). It is reachable without a token.
- [ ] FR-002: `GET /api/posts/me` returns the authenticated caller's own non-deleted posts in every status, newest first, with the same pagination bounds. It requires a token.
- [ ] FR-003: `GET /api/posts/feed` no longer exists; a request to it is not served by a feed handler.
- [ ] FR-004: `GET /api/recipes` returns a page of `APPROVED`, non-deleted recipes, newest `createdAt` first, using `page`/`size` and the shared `PageResponse` envelope. It is reachable without a token and returns an empty page when no recipe is approved.
- [ ] FR-005: `GET /api/recipes/{recipeId}` returns the recipe when its `moderationStatus` is `APPROVED` and it is not soft-deleted, regardless of who authored it, with or without a token.
- [ ] FR-006: `GET /api/recipes/{recipeId}` still returns the caller's own recipe in any status (`PENDING`, `APPROVED`, `REJECTED`), so an author never loses access to what they submitted.
- [ ] FR-007: `GET /api/recipes/{recipeId}` answers `404 Recipe not found` when the recipe is missing, soft-deleted, or not approved and the caller is not its author.
- [ ] FR-008: `rejectionReason` is returned only to the recipe's author. It is `null` for every other caller, including guests.
- [ ] FR-009: `POST /api/recipes` and `GET /api/recipes/me` remain authenticated.

### Non-Functional Requirements

- [ ] NFR-SEC-001: No non-author read can reveal an unapproved recipe's body, instructions, or `rejectionReason`.
- [ ] NFR-SEC-002: Public recipe routes are explicit `permitAll` rules in `SecurityConfig`; every other recipe route stays behind the authenticated catch-all.
- [ ] NFR-SCALE-001: Both list endpoints return a bounded page (maximum `100`) instead of an unbounded result set.
- [ ] NFR-MAINT-001: Recipe list and detail reuse `RecipeService.toResponse` and the existing `RecipeResponse` shape rather than introducing a second mapping.

## Design overview

`PostController` swaps the mappings: the literal `@GetMapping` becomes the public feed and the new `@GetMapping("/me")` carries `@PreAuthorize("isAuthenticated()")`. `SecurityConfig` permits `GET /api/posts` and keeps `GET /api/posts/me` authenticated.

`RecipeController` loses its class-level `@PreAuthorize`/`@SecurityRequirement`; `POST /api/recipes` and `GET /api/recipes/me` keep both at the method level, while `GET /api/recipes` and `GET /api/recipes/{recipeId}` are public. `RecipeService` gains `listApprovedRecipes(request)` backed by a new paginated repository query, and `getRecipe` changes from an owner-only filter to "approved or author". A public non-author read clears `rejectionReason` before returning.

No schema change is needed: visibility is already stored in `recipe.moderation_status` and `recipe.deleted_at`.

## Success metrics

Before merge, the integration suite covers: a guest reading the approved list, a guest opening an approved recipe, a non-author getting `404` for a pending recipe, and the author still reading their own pending/rejected recipe. The post suite confirms the feed at `GET /api/posts` and the own list at `GET /api/posts/me`. No production metric is available yet.

## Acceptance criteria

**As a** visitor or member, **I want to** browse and open approved recipes without being their author, **so that** community recipes are discoverable; **as a** member, **I want** my own post list at `GET /api/posts/me`, **so that** the API matches the recipe routes.

- [ ] Given a guest, when `GET /api/recipes` is called, then only `APPROVED`, non-deleted recipes are returned, newest first, in a page envelope.
- [ ] Given a guest, when `GET /api/recipes/{recipeId}` is called for an approved recipe, then the full recipe is returned with `rejectionReason` null.
- [ ] Given a non-author, when `GET /api/recipes/{recipeId}` is called for a `PENDING` or `REJECTED` recipe, then the response is `404`.
- [ ] Given the author, when `GET /api/recipes/{recipeId}` is called for their own `PENDING` recipe, then it is returned with its state and `rejectionReason`.
- [ ] Given a guest, when `GET /api/posts` is called, then the published feed is returned and a token is not required.
- [ ] Given a valid token, when `GET /api/posts/me` is called, then only the caller's non-deleted posts are returned in every status.
- [ ] Given any caller, when `GET /api/posts/feed` is called, then no feed handler serves it.

## Risks / open questions

- Removing `GET /api/posts/feed` and moving the own list is a breaking change for existing clients; the frontend must switch to `GET /api/posts` and `GET /api/posts/me` in the same release.
- A recipe page resolves ingredients per recipe, so a page of N recipes costs N ingredient queries. This matches the existing saved-recipe read path and is bounded by the `size` maximum; a batched lookup is a separate optimisation if it becomes hot.
- `GET /api/posts/feed` will now fall through to the `/{postId}` mapping and fail UUID parsing rather than return a redirect or `404`. That is acceptable for a removed path but should be called out in release notes.
