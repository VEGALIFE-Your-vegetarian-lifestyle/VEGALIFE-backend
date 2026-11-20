# API Reference: PATCH /api/posts/{postId}/visibility — REMOVED

## Status

**Removed** by [ADR-023](../../adrs/023-post-processing-status-and-flag-blocker.md). This page is
retained only as a record of the endpoint that existed before the post moderation model was reduced
to `status` + `flag`.

## What it did

```text
PATCH /api/posts/{postId}/visibility
Body: { "hidden": true | false }
```

An Administrator could hide a post (`status = hidden`, `publishedAt` cleared) or lift the hide
(back to `created`), logging `HIDE_POST` / `UNHIDE_POST` in `moderation_log`.

## Why it was removed

`hidden` was a second, parallel way to take content out of public view, overlapping with
`unpublished` and with the author's own withdrawal. Keeping both meant every reader had to know two
rules for "not visible", and the owner of a hidden post could not withdraw or re-submit it. The
model now has exactly one negative terminal state, and only two ways to reach it:

- an Administrator rejects the post → `status = unpublished` (`POST /api/admin/posts/{postId}/reject`);
- the author withdraws it → `status = drafted` (`PATCH /api/posts/{postId}` with `"publish": false`).

The route no longer exists: a request to it is answered `404 Not Found`.

## Replacement

| Instead of | Use |
|------------|-----|
| Hide a post | `POST /api/admin/posts/{postId}/reject` → `unpublished` |
| Lift a hide | `POST /api/admin/posts/{postId}/approve` → `published`, or the author edits and resubmits |
| Author withdraws | `PATCH /api/posts/{postId}` with `{ "publish": false }` → `drafted` |

## Related

- Decision: `docs/adrs/023-post-processing-status-and-flag-blocker.md`
- Business rules: `docs/brs/posts.md` (BR-POST-009 is retired, BR-POST-012 covers approve/reject)
- Edit a post: `docs/apis/post/patch-posts-postid.md`
- Approve/reject: `docs/apis/admin/post-posts-postid-approve.md`
