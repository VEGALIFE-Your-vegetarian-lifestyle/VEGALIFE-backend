# Feature Spec: Delete Comments API

## Status

In progress

## Author / owner

Backend team; driving issue: [#43](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/43)

## Summary

Let a comment's owner remove their own comment, and let an Administrator remove any comment, via a soft delete that keeps the thread's shape intact.

## Problem / motivation

Users need to remove their own comments, and Administrators need to moderate abusive ones. A hard delete would break reply threads (a reply's `parentId` would point at nothing) and destroy the moderation trail, so this follows the same soft-delete approach already used for posts (`BR-POST-008`).

## Goals

- Let the comment's owner soft-delete their own comment.
- Let an Administrator soft-delete any comment, with the override recorded in `moderation_log` (`BR-ADMIN-002`).
- Preserve thread structure: a deleted comment's replies are untouched and keep pointing at it.

## Non-goals

- Hard delete (no row is ever physically removed by this endpoint).
- Bulk delete (one comment per call).
- Rendering "[deleted]" in a comment thread view — there is no public comment-read/thread endpoint in this codebase yet (see `docs/feats/create-comments-api.md`'s open question and the separate, not-yet-built "List Comments API" backlog item). This endpoint only performs the soft delete; a future thread-read endpoint is responsible for displaying removed comments as "[deleted]" by checking `deletedAt`, the same way `AdminCommentMapper` already derives `status: "removed"` for the Admin list.

## Requirements

### Functional Requirements

- [ ] FR-001: `DELETE /api/posts/{postId}/comments/{commentId}` requires an authenticated user and soft-deletes (`deleted_at = now()`) the comment identified by `commentId` under post `postId`.
- [ ] FR-002: A non-admin may only delete their own comment; a comment owned by someone else, already deleted, or not found is reported as `404` (ownership is not disclosed to a non-owner, matching `BR-POST-006`'s pattern for posts).
- [ ] FR-003: An Administrator may delete any non-deleted comment under the given post.
- [ ] FR-004: Deleting someone else's comment as an Administrator writes one `moderation_log` row (`action = DELETE_COMMENT`, `target_type = COMMENT`, `target_id` = the comment's id, `actor_id` = the admin). Deleting your own comment, admin or not, is not logged.
- [ ] FR-005: Deletion never touches other rows: a deleted comment's replies keep their `parentId` unchanged, so the thread shape survives.
- [ ] FR-006: Success returns `200 OK` with no data payload.

### Non-Functional Requirements

- [ ] NFR-SEC-001: Requests without a valid JWT are rejected with `401 Unauthorized`.
- [ ] NFR-MAINT-001: Reuses the existing `comment.deleted_at` column (already present since `V8`) and the existing `moderation_log` table; no new migration is required.

## Design overview

Add `DELETE /api/posts/{postId}/comments/{commentId}` to `CommentController`, mirroring `PostController.deletePost`'s `isAdmin(authentication)` helper. `CommentService.deleteComment` loads the comment scoped by ownership (admin: any non-deleted comment on the post; non-admin: only their own non-deleted comment on the post) via a new `CommentRepository.findByIdAndPostIdAndUserIdAndDeletedAtIsNull` query, sets `deletedAt`, saves, and — only when an Administrator deletes someone else's comment — records a `moderation_log` entry, the same pattern `PostService.recordModerationIfAdminOverride` uses for posts.

## Success metrics

All acceptance scenarios below pass in automated tests before merge.

## Acceptance criteria

**As** a comment owner or an Administrator, **I want to** remove a comment, **so that** I can take down my own mistake or moderate abusive content, without breaking the thread for other participants.

- [ ] Given the comment owner, when they delete their own comment, then it is soft-deleted (`200 OK`) and no moderation log entry is written.
- [ ] Given an Administrator who is not the comment's owner, when they delete it, then it is soft-deleted (`200 OK`) and a `DELETE_COMMENT` moderation log entry is written for that admin and comment.
- [ ] Given an Administrator deleting their own comment, then it is soft-deleted and no moderation log entry is written (not an override).
- [ ] Given an authenticated non-admin who is not the comment's owner, when they attempt to delete it, then `404 Not Found` is returned and the comment is unchanged.
- [ ] Given a missing or invalid JWT, when the endpoint is called, then `401 Unauthorized` is returned and no comment is deleted.
- [ ] Given a comment that is already deleted, belongs to a different `postId`, or does not exist, when the endpoint is called, then `404 Not Found` is returned.
- [ ] Given a comment with replies, when it is deleted, then the replies still exist with their original `parentId` pointing at the deleted comment.

## Risks / open questions

- Criterion "Deleted comments appear as '[deleted]' in the thread" from issue #43 cannot be end-to-end verified today: there is no public endpoint that reads a post's comment thread. This spec implements the soft-delete side only; whichever feature adds thread reading must map a non-null `deletedAt` to a `"[deleted]"` placeholder (and likely strip `content`) rather than exposing the original text to non-admin readers.
- Restoring a deleted comment is not supported, matching the posts convention.

---

## Related

- API reference: `docs/apis/post/delete-posts-postid-comments-commentid.md`
- Business rule: `docs/brs/comments.md` (`BR-COMMENT-003`)
- Related admin surface: `docs/apis/admin/get-comments.md` (already shows `status: "removed"` for soft-deleted comments)
- Prior features: `docs/feats/create-comments-api.md` (issue #41), `docs/feats/edit-comments-api.md` (issue #42)
