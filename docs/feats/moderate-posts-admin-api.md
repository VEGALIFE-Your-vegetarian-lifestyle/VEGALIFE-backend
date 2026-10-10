# Feature Spec: Moderate Posts API (Admin)

## Status

In progress

## Author / owner

Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/5

## Summary

Let an Administrator approve a post that semantic filtering withheld (publish it) or take a
published post down (unpublish it), recording who acted and why in `moderation_log`, and make the
admin post list surface posts awaiting review by default.

## Problem / motivation

The content filter (issue #33/34, ADR-007) withholds a post when its verdict is `REJECTED` or
`NEEDS_REVIEW`, leaving it `status = unpublished` with its verdict preserved on `flag` (ADR-011).
Admins can currently only *hide* a post (`PATCH /api/posts/{postId}/visibility`) — hide is a
stronger, separate lever (status `hidden`, clears the publication timestamp, returns to draft on
lift). There is no way for an admin to **override the filter and publish** a withheld post, nor to
**unpublish** a live post as a moderation decision, and `moderation_log` cannot record *why* an
admin acted — its columns are actor/action/target/time only (`V18__create_moderation_log.sql`).

The admin post list (`GET /api/admin/posts`, issue #1) can filter by `flag`, but returns everything
when no filter is supplied, so an admin opening it does not land on the posts that need a decision.

## Goals

- An Administrator can publish a withheld/non-public post, overriding the filter verdict, and it
  becomes publicly visible under the normal publish rules.
- An Administrator can unpublish a post, removing it from the public feed.
- Every such action is recorded in `moderation_log` with the acting admin and a reason.
- The admin post list defaults to showing posts awaiting review (`flag = NEEDS_REVIEW`), and the
  admin can change or clear that filter.
- Non-admins cannot call the moderation action (403); anonymous callers get 401.

## Non-goals

- Content editing by an admin (use `PATCH /api/posts/{postId}`, which already logs admin overrides).
- Bulk moderation (multi-post publish/unpublish in one call).
- Automated moderation rules (the filter keeps its own verdict logic unchanged).
- Changing a post's `flag` (the filter verdict). Admin publish/unpublish touches `status` only;
  `flag` is left exactly as the filter wrote it (ADR-011: one axis per concern).
- Reworking the existing hide/unhide endpoint (`PATCH /api/posts/{postId}/visibility`); `hidden`
  posts are not publish/unpublish targets.

## Requirements

### Functional Requirements

- [ ] FR-001: `POST /api/admin/posts/{postId}/moderate` accepts `{ "action": "PUBLISH" | "UNPUBLISH", "reason": "<text>" }`.
- [ ] FR-002: `PUBLISH` sets `status = published` and `publishedAt = now`, leaving `flag` unchanged.
- [ ] FR-003: `UNPUBLISH` sets `status = unpublished` and clears `publishedAt`, leaving `flag` unchanged.
- [ ] FR-004: `PUBLISH` requires the post to have at least one category, mirroring BR-CONTENT-003; otherwise 400.
- [ ] FR-005: Each state change writes a `moderation_log` row: `actor_id` = admin, `action` = `PUBLISH_POST` / `UNPUBLISH_POST`, `target_type` = `POST`, `target_id` = post, `reason` = request reason.
- [ ] FR-006: A no-op request (publish an already-`published` post, or unpublish an already-`unpublished` post) returns the post unchanged with no new log row.
- [ ] FR-007: Soft-deleted or missing posts are reported as 404; a `hidden` post is rejected with 400 (out of scope of these verbs).
- [ ] FR-008: `GET /api/admin/posts` defaults `flag` to `NEEDS_REVIEW` when the parameter is absent.
- [ ] FR-009: `GET /api/admin/posts?flag=all` disables the flag filter (returns every flag including null), applied in the service layer.

### Non-Functional Requirements

- [ ] NFR-SEC-001: The endpoint is restricted to `ROLE_ADMIN` via `@PreAuthorize("hasRole('ADMIN')")` (ADR-009); there is no `/api/admin/**` path rule.
- [ ] NFR-SEC-002: No account secret is exposed; the response is the existing `AdminPostListResponse`.
- [ ] NFR-MAINT-001: Response wrapped in `ApiResponse<T>`; controller stays thin, logic in `AdminPostService`.
- [ ] NFR-MAINT-002: The new `moderation_log.reason` column is nullable and additive (migration `V32`), so existing writers keep working.

## Design overview

New `PostModerationRequest` DTO (`dto/request/admin`) and a `moderatePost` method on the existing
`AdminPostService`; a new handler on the existing `AdminPostController` at
`POST /api/admin/posts/{postId}/moderate`, which already carries
`@PreAuthorize("hasRole('ADMIN')")` at class level. The service loads a non-deleted post
(`ResourceNotFoundException`), no-ops if the target status already matches, enforces the category
rule on publish (reusing the same check `PostService` applies, BR-CONTENT-003), flips `status` (and
`publishedAt`), and writes a `moderation_log` row including the new `reason`. `flag` is deliberately
untouched so the filter verdict stays auditable (ADR-011). Migration
`V32__add_moderation_log_reason.sql` adds the nullable `reason` column; the `ModerationLog` entity
gains the matching field. The admin post list gains a default filter: `PostListRequest.getFlag()`
returns `NEEDS_REVIEW` when the parameter is absent, and the literal `all` is translated to "no
filter" in `AdminPostService.parseFlag` (the data layer is unchanged — `PostSpecifications` still
takes a nullable `Post.Flag`).

## Success metrics

- Issue #5's four acceptance criteria are covered by integration tests before PR merge.
- An admin opening `GET /api/admin/posts` with no parameters sees only `NEEDS_REVIEW` posts.

## Acceptance criteria

**As an** admin, **I want to** publish or unpublish a post and record why, **so that** I can act on
filter-flagged content without database access.

- [ ] Given an admin JWT and a non-public post, when moderating it with `action=PUBLISH`, then its status becomes `published` (and it appears in the feed) while its `flag` is unchanged.
- [ ] Given an admin JWT and a published post, when moderating it with `action=UNPUBLISH`, then its status becomes `unpublished` and it leaves the feed.
- [ ] Given an admin JWT, when a moderation action succeeds, then a `moderation_log` row exists with the admin's id, the action, the post id, and the supplied reason.
- [ ] Given no `flag` query parameter, when an admin lists posts, then only posts with `flag = NEEDS_REVIEW` are returned; `?flag=all` returns every flag.
- [ ] Given a non-admin JWT, when moderating a post or listing admin posts, then 403 is returned; given no JWT, 401.

## Risks / open questions

- **Behavior change to an existing endpoint:** `GET /api/admin/posts` now defaults to
  `flag=NEEDS_REVIEW`. Any caller relying on "no filter by default" must pass `?flag=all`. Decided
  deliberately for issue #5's "flagged posts first" criterion; documented in the API reference.
- **`flag` left stale is intentional:** a post published by admin override can still read
  `flag=REJECTED`/`NEEDS_REVIEW`. ADR-011 keeps one axis per concern; the verdict is a record of what
  the filter said, not of the post's current visibility.
- `reason` is optional in the request; the row stores NULL when omitted. If a mandatory reason is
  later required, that is a separate change to validation, not to the schema.
