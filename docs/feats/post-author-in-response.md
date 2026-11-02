# Feature Spec: Post Responses Carry Their Author's Identity

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Every member-facing post response now carries an author summary — `userId`,
`displayName`, and `avatarUrl` — so a client can render a post's byline from the
post payload alone, without a second request per post and without guessing which
account wrote it.

## Problem / motivation

`PostListResponse` returned the post's own content and metadata but nothing about
who wrote it. A feed or detail page therefore had to either omit the author or
issue one profile request per post, which is both slow and — after ADR-019 — the
only reliable way to identify the author at all, since a display name is not
unique. The post payload has to name the author by `userId` for the client to
link anywhere, and has to carry the display name and avatar for the byline to be
renderable without extra work.

## Goals

- Add `userId`, `displayName`, and `avatarUrl` to `PostListResponse`, and
  therefore to every endpoint that returns it.
- Resolve the avatar in one batched query per response, never one per post.
- Leave the post's own fields unchanged.

## Non-goals

- Follow/unfollow, author profile expansion, or post counts on the author.
- Changing who may read a post.
- Adding author data to the vote or comment payloads (comments already carry it;
  votes are out of scope).

## Requirements

### Functional Requirements

- [x] FR-001: `PostListResponse` includes `userId` (uuid), `displayName`
      (string), and `avatarUrl` (string or null) alongside the existing fields.
- [x] FR-002: The three author fields are present on every endpoint that returns
      `PostListResponse`: `POST /api/posts`, `GET /api/posts/{postId}`,
      `PATCH /api/posts/{postId}`, `PATCH /api/posts/{postId}/visibility`,
      `POST /api/posts/{postId}/media`, `POST /api/posts/{postId}/recipes`,
      `GET /api/posts`, `GET /api/posts/feed`,
      `GET /api/users/{userId}/posts`, and `GET /api/posts/{postId}/related`
      (whose items embed the same shape).
- [x] FR-003: `userId` is the author's account id — the stable way to address
      the author, since display names are not unique (ADR-019).
- [x] FR-004: `avatarUrl` is `null`, not omitted and not an error, when the
      author has no profile row yet (BR-PROFILE-003).
- [x] FR-005: A post whose author is soft-deleted still returns that author's
      `userId` and `displayName`: the post is what was deleted, not the account.

### Non-Functional Requirements

- [x] NFR-PERF-001: One avatar lookup per response — a `findByUserIdIn` batch
      over the page's distinct authors — so adding author data does not make list
      latency grow with page size.
- [x] NFR-PERF-002: The author is fetched with the post (`@EntityGraph`) on the
      list, detail, and related-posts queries, so mapping `userId`/`displayName`
      does not trigger a lazy load per row.
- [x] NFR-MAINT-001: The author summary lives in the existing `PostListResponse`
      and `PostMapper`; no parallel DTO is introduced.

## Design overview

`PostListResponse` gains `userId`, `displayName`, and `avatarUrl`. `PostMapper`
maps the first two straight from `post.user` in both `toListResponse` overloads
and ignores `avatarUrl`, because the avatar lives on `UserProfile`, which the
mapper cannot reach. `PostService` therefore resolves avatars itself: a private
`applyAuthorAvatars(...)` collects the distinct author ids from a batch of
responses, issues exactly one `UserProfileRepository.findByUserIdIn` query, and
fills each `avatarUrl`; single-post paths call it with a one-element list, page
paths with the whole page, and the related-posts path with all matched posts
after they are assembled. `PostRepository`'s list, detail, and
related-by-id queries carry `@EntityGraph(attributePaths = "user")` so the author
row arrives with the post rather than as an N+1.

The avatar map is built with a plain loop rather than `Collectors.toMap`, because
`avatarUrl` is nullable and `toMap` rejects null values.

## Success metrics

All acceptance scenarios pass in unit and integration tests, including a
list-response assertion that each item carries the author's `userId` and
`displayName`, and that an author without a profile row yields `avatarUrl: null`.

## Acceptance criteria

**As a** reader of a feed or post detail page, **I want** each post to name and
depict its author, **so that** I can render the byline and link to the author's
profile without extra requests.

- [x] Given a post list, when the response is inspected, then each item carries
      `userId`, `displayName`, and `avatarUrl` for the post's author.
- [x] Given a post detail request, when the post exists, then the response
      carries the same author fields.
- [x] Given a page whose posts share an author, when the page is served, then the
      avatar is resolved once, not once per post.
- [x] Given an author with no `user_profile` row, when their post is returned,
      then `avatarUrl` is `null` and the rest of the response is unaffected.
- [x] Given a related-posts request, when matches are returned, then each embedded
      post carries its own author fields.

## Risks / open questions

- None. The author relation already existed on `Post`; this only exposes it.

---

## Related

- API reference: `docs/apis/post/get-posts.md`, `docs/apis/post/get-posts-postid.md`,
  `docs/apis/post/get-posts-feed.md`, `docs/apis/post/get-posts-postid-related.md`
- ADR: `docs/adrs/019-display-name-instead-of-username.md`
- Business rules: `docs/brs/posts.md`, `docs/brs/profile.md` (BR-PROFILE-003)
