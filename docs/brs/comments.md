# Business Rules: Comments

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-COMMENT-001 | Comment Creation Requires Authentication; Reading Is Public | Active | 2026-10-10 |

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
