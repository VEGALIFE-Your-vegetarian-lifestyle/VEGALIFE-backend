# Business Rules: Votes

## Rule Index

| Rule ID | Title | Status | Last Reviewed |
|---------|-------|--------|---------------|
| BR-VOTE-001 | One Vote Per User Per Post, Toggled | Active | 2026-10-11 |
| BR-VOTE-002 | Voting Requires Authentication; Reading Counts Is Public | Active | 2026-10-11 |

---

# Business Rule: One Vote Per User Per Post, Toggled

## Rule ID

`BR-VOTE-001`

## Status

Active

## Statement

A user has at most one vote (`UPVOTE` or `DOWNVOTE`) on a given post, enforced by the `vote` table's `UNIQUE (user_id, post_id)` constraint. Casting a vote equal to the user's existing vote on that post removes it. Casting the other vote type replaces the existing one. A first-time vote simply creates the row. Only published, non-deleted posts can be voted on.

## Rationale

Issue #45 requires a vote to be "toggled between like, dislike, or removed" — a single `PUT` action covers all three outcomes without a separate delete endpoint, matching the resource semantics of "my vote for this post."

## Scope & Exceptions

Applies to `PUT /api/posts/{postId}/votes` and `GET /api/posts/{postId}/votes`. Voting on comments is explicitly out of scope (issue #45 non-goal); so is vote history/analytics — only the current vote and aggregate counts are kept.

## Enforcement

- Service: `VoteService.castVote()` looks up the caller's existing vote for the post and branches: absent → insert; same type → delete; different type → update.
- The post must resolve via `PostRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published)`, the same visibility check `CommentService` uses for comments.
- API reference: `docs/apis/post/put-posts-postid-votes.md`.

## Last Reviewed

2026-10-11, by Vegalife backend team

---

# Business Rule: Voting Requires Authentication; Reading Counts Is Public

## Rule ID

`BR-VOTE-002`

## Status

Active

## Statement

Casting or changing a vote requires an authenticated user. Reading a post's vote counts requires no authentication; if the request carries a valid JWT, the response also includes that caller's own current vote, otherwise `userVote` is `null`.

## Rationale

Vote totals are public signal (and feed recommendations per the issue's "why"), so anonymous visitors should see them, while only an identified user's own vote is meaningful to disclose per-caller.

## Scope & Exceptions

Applies to `PUT /api/posts/{postId}/votes` (authenticated) and `GET /api/posts/{postId}/votes` (public, optionally personalized). This is the first endpoint in the project to read an optional, best-effort identity on an otherwise-public route rather than being strictly public or strictly authenticated.

## Enforcement

- `PUT` carries `@PreAuthorize("isAuthenticated()")`.
- `GET` carries no `@PreAuthorize` and is allowlisted as `permitAll` for `GET` in `SecurityConfig`, per ADR-009's model; `EndpointAuthorizationGuardTest` enforces that every handler is either annotated or on that allowlist.
- `VoteController` resolves the optional viewer identity itself: `authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken` means anonymous (`userVote: null`); otherwise the JWT principal (a `UUID`) is used.
- API reference: `docs/apis/post/get-posts-postid-votes.md`.

## Last Reviewed

2026-10-11, by Vegalife backend team
