# API Reference: POST /api/admin/posts/{postId}/moderate — REMOVED

> **This endpoint no longer exists.** Since ADR-016 it returns `404`. It is kept in
> the repository only as a record of what replaced it. Do not implement against it.

## Why it was removed

`/moderate` took an action of `PUBLISH` or `UNPUBLISH`, which conflated two different
questions: *is this post acceptable?* and *is this post visible?* Once a dish or
ingredient name can be pending admin review, approving a post also has to approve the
names it introduced — and rejecting it has to refuse the ones still unanswered. An
action enum could not express that, and a client that only published would leave
names stranded in `PENDING` forever.

## What replaced it

| Old | New |
|---|---|
| `POST /api/admin/posts/{postId}/moderate` with `action: PUBLISH` | `POST /api/admin/posts/{postId}/approve` |
| `POST /api/admin/posts/{postId}/moderate` with `action: UNPUBLISH` | `POST /api/admin/posts/{postId}/reject` |
| — | `GET /api/admin/posts/{postId}` — detail with scores and component states |

There is no separate unpublish endpoint: reject subsumes it. An admin who only wants
a post off the feed has no reason to leave its names pending.

The request body lost its `action` field; both new endpoints accept an optional
`reason` and nothing else.

**Breaking change for clients:** this is a removal, not a deprecation. A frontend
still calling `/moderate` must ship the new paths in the same release.

## Behaviour carried over unchanged

- Only an Administrator may approve or reject a post (BR-POST-012).
- Publishing requires at least one category (BR-CONTENT-003).
- A `hidden` post is refused — it is moderated through
  `PATCH /api/posts/{postId}/visibility`.
- `flag` is never touched: an approval does not rewrite the filter's verdict
  (ADR-011).
- Every real change writes a `moderation_log` row, now `APPROVE_POST` /
  `REJECT_POST`, with actor, target and reason (BR-ADMIN-002).

One behaviour deliberately did **not** carry over: a repeat approval or rejection is
no longer a silent no-op returning the post. It still returns `200`, but the
component pass runs too, so a name left `PENDING` after an earlier call is approved
by the next one.

## Related

- [Admin moderation of posts: approve and reject](post-posts-postid-approve.md) — the replacement
- [ADR-016: Component-aware moderation](../../adrs/016-component-level-moderation.md)
- [Feature spec: component-level verification](../../feats/component-level-verification.md)
- [List posts (Admin)](get-posts.md)
