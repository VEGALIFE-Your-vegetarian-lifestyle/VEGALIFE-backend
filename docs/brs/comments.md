# Business Rules: Comments

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-COMMENT-001 | Comment Creation Requires Authentication; Reading Is Public | Active | 2026-10-10 |
| BR-COMMENT-002 | Only the Owner May Edit a Comment | Active | 2026-10-11 |
| BR-COMMENT-003 | Comment Deletion Is Owner-or-Admin and Soft | Active | 2026-10-11 |

---

# Business Rule: Comment Creation Requires Authentication; Reading Is Public

## Rule ID

`BR-COMMENT-001`

## Status

Active

## Statement

Only a logged-in, authorized user may post a comment. Anyone, including a guest who has not logged in, may read existing comments.

## Rationale

Authentication ties new comments to accountable authors, while public reading lets visitors participate in discussions without an account.

## Scope & Exceptions

Applies to comment creation and comment reads. Issue #41 implements comment creation only. The rule's public-read behavior requires a public retrieval endpoint, which is not currently available; `GET /api/admin/comments` is restricted to Administrators and is not a public read path.

## Enforcement

- Comment creation derives the author from a valid JWT principal and is protected by the default authenticated route policy.
- Public read enforcement is pending a public comment retrieval API.
- API reference for creation: `docs/apis/post/post-posts-postid-comments.md`.

## Last Reviewed

2026-10-10, by Vegalife backend team

---

# Business Rule: Only the Owner May Edit a Comment

## Rule ID

`BR-COMMENT-002`

## Status

Active

## Statement

Only the authenticated user who created a comment may edit its content. No other user, including an Administrator, may edit someone else's comment. Edits have no time limit.

## Rationale

Editing is scoped narrowly to typo/content fixes by the original author. The product requirements document's original comment moderation rule named Administrator as an exception for edit/delete, but issue #42's acceptance criteria and explicit non-goal ("moderator override of edits") scope this feature to owner-only edits; broader Administrator moderation of comment content, if ever needed, is a deliberately separate decision with its own logging requirements, not a side effect of this rule.

## Scope & Exceptions

Applies to comment edits (`PATCH /api/posts/{postId}/comments/{commentId}`). Does not apply to comment creation (`BR-COMMENT-001`) or to comment deletion, which is a separate, not-yet-implemented feature.

## Enforcement

- The service loads the comment and compares its `userId` to the authenticated principal; a mismatch raises `403 Forbidden` regardless of the caller's role.
- No elapsed-time check is performed against `createdAt`.
- API reference: `docs/apis/post/patch-posts-postid-comments-commentid.md`.

## Last Reviewed

2026-10-11, by Vegalife backend team

---

# Business Rule: Comment Deletion Is Owner-or-Admin and Soft

## Rule ID

`BR-COMMENT-003`

## Status

Active

## Statement

A comment may be deleted only by its owner or an Administrator. Deletion is soft: `deleted_at` is set and the row is kept, so replies keep pointing at it and the thread shape is preserved. For a non-admin, a missing, already-deleted, or other user's comment is reported as not found. Every deletion an Administrator makes to another user's comment is recorded in `moderation_log` (`BR-ADMIN-002`); deleting your own comment, admin or not, is not logged.

## Rationale

Unlike editing (`BR-COMMENT-002`, deliberately owner-only per issue #42), deletion explicitly includes an Administrator moderation path per issue #43 — abusive content must be removable by moderators. Soft deletion (the same approach as `BR-POST-008`) avoids breaking reply threads that a hard delete would orphan, and keeps a moderation trail.

## Scope & Exceptions

Applies to `DELETE /api/posts/{postId}/comments/{commentId}`. Does not apply to comment creation (`BR-COMMENT-001`) or editing (`BR-COMMENT-002`). Restoring a deleted comment is not supported. Rendering deleted comments as "[deleted]" is the responsibility of a future comment-thread read endpoint, not this rule — see the open question in `docs/feats/delete-comments-api.md`.

## Enforcement

- Controller: `CommentController.deleteComment()` supplies the JWT principal and admin flag (mirrors `PostController.isAdmin()`).
- Service: `CommentService.deleteComment()` scopes the lookup by ownership unless the caller is an Administrator, and logs only an admin's cross-user override.
- API reference: `docs/apis/post/delete-posts-postid-comments-commentid.md`.

## Last Reviewed

2026-10-11, by Vegalife backend team
