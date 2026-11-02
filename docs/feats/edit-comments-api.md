# Feature Spec: Edit Comments API

## Status

In progress

## Author / owner

Backend team; driving issue: [#42](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/42)

## Summary

Let the owner of a comment update its plain-text content, with no time limit, and mark the comment as edited.

## Problem / motivation

Users make typos in comments and currently have no way to fix them short of deleting and re-posting (and comment deletion isn't implemented either). Issue #42 asks for an in-place edit that updates the content and surfaces an "edited" indicator to readers.

## Goals

- Let the comment's owner update its `content`.
- Allow edits at any time after creation (no time window).
- Mark an edited comment so clients can show an "edited" indicator.
- Reject edits from anyone other than the owner.

## Non-goals

- Edit history (no audit trail of previous content).
- Moderator/Administrator override — only the owner may edit. (This intentionally narrows `BR-COMMENT-002` as originally drafted in the product requirements document, which named Administrator as an exception; issue #42's acceptance criteria and non-goals both scope this feature to owner-only edits. Admin moderation of comments, if ever added, is a separate feature.)
- Editing `parentId` or moving a comment to a different thread/post.

## Requirements

### Functional Requirements

- [ ] FR-001: `PATCH /api/posts/{postId}/comments/{commentId}` requires an authenticated user and updates the `content` of the comment identified by `commentId` under post `postId`.
- [ ] FR-002: The request requires non-blank plain-text `content`.
- [ ] FR-003: Only the comment's owner (the authenticated user whose ID matches the comment's `userId`) may perform the edit; any other authenticated user (including an Administrator) receives `403 Forbidden`.
- [ ] FR-004: The target comment must exist, belong to `postId`, and not be soft-deleted; otherwise the API returns `404`.
- [ ] FR-005: On a successful edit, the comment's `is_edited` flag is set to `true` (once set, it stays `true` on subsequent edits) and `updated_at` reflects the edit time.
- [ ] FR-006: Success returns `200 OK` with the comment's `id`, `postId`, `parentId`, `userId`, `content`, `edited`, `createdAt`, and `updatedAt`.
- [ ] FR-007: There is no time limit on when an edit can be made relative to `createdAt`.

### Non-Functional Requirements

- [ ] NFR-SEC-001: Requests without a valid JWT are rejected with `401 Unauthorized`.
- [ ] NFR-SEC-002: The endpoint does not accept a client-supplied `userId`, `postId` (path only), or `is_edited`.
- [ ] NFR-MAINT-001: Reuses the existing `comment` table and layered controller/service/repository structure; adds one migration for the new `is_edited` column.

## Design overview

Add an `is_edited` boolean column to `comment` (default `false`), surfaced on the entity and the response DTO as `edited`. Add a `PATCH /api/posts/{postId}/comments/{commentId}` endpoint on the existing `CommentController`, backed by a new `CommentService.updateComment` method that:

1. Loads the comment via the existing `findByIdAndPostIdAndDeletedAtIsNull` query (404 if absent).
2. Throws `ForbiddenException` (403) if the authenticated user isn't the comment's owner.
3. Updates `content` and sets `isEdited = true`.
4. Saves and returns the mapped response.

## Success metrics

All acceptance scenarios below pass in automated tests before merge.

## Acceptance criteria

**As the** owner of a comment, **I want to** edit its content at any time, **so that** I can fix mistakes without deleting and reposting.

- [ ] Given the comment owner and valid non-blank content, when they edit their own comment, then the content updates, `edited` becomes `true`, and `200 OK` is returned.
- [ ] Given the comment owner edits the same comment a second time, then `edited` remains `true`.
- [ ] Given an authenticated user who is not the comment's owner (including an Administrator), when they attempt to edit, then `403 Forbidden` is returned and the content is unchanged.
- [ ] Given a missing or invalid JWT, when the endpoint is called, then `401 Unauthorized` is returned and no change is made.
- [ ] Given blank content, a missing/deleted comment, or a comment belonging to a different `postId`, when the endpoint is called, then it returns the documented client error and no change is made.
- [ ] Given a comment created long ago, when the owner edits it, then the edit succeeds regardless of elapsed time (no time-limit check exists).

## Risks / open questions

- The original product requirements document (business rules section) names Administrator as an exception for comment edit/delete. This spec deliberately follows issue #42 instead (owner-only edit); if Admin moderation of comments becomes a requirement later, it should be a separate, explicitly scoped feature with its own moderation-logging behavior, not a silent addition here.
- Comment deletion (also owner-only per issue, with its own ticket) is out of scope for this change.

---

## Related

- API reference: `docs/apis/post/patch-posts-postid-comments-commentid.md`
- Business rule: `docs/brs/comments.md` (`BR-COMMENT-002`)
- Driving issue: [#42](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/42)
- Prior feature: `docs/feats/create-comments-api.md` (issue #41)
