# Feature Spec: Vote (Like/Dislike) API

## Status

In progress

## Author / owner

Backend team; driving issue: [#45](https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/45)

## Summary

Let an authenticated user like or dislike a post, with one vote per user per post that toggles between like, dislike, and removed. Expose the post's current vote counts and the caller's own vote.

## Problem / motivation

Users want to express sentiment on posts, and votes are a recommendation signal. The `vote` table already exists (created alongside `comment` in `V8`) but has no API.

## Goals

- Let an authenticated user cast, switch, or remove their vote on a published post with a single action.
- Enforce exactly one vote per user per post.
- Return vote counts and the caller's own vote from the vote action itself.
- Let anyone read a post's vote counts; show the caller's own vote too when they're identified.

## Non-goals

- Voting on comments (explicitly deferred per issue #45).
- Vote history or analytics (only current state and aggregate counts are kept; no event log).
- Surfacing vote counts or the viewer's vote on the existing post endpoints (`GET /api/posts/{postId}`, `GET /api/posts`, `GET /api/posts/feed`, etc.). Those endpoints and their shared `PostListResponse`/`PostMapper` are deliberately left untouched — adding a viewer-aware vote summary to every post read path is a much larger, cross-cutting change than this issue's scope, and was explicitly decided against in favor of a dedicated vote resource.

## Requirements

### Functional Requirements

- [ ] FR-001: `PUT /api/posts/{postId}/votes` requires an authenticated user and casts, switches, or removes that user's vote on the post identified by `postId`, per `BR-VOTE-001`.
- [ ] FR-002: The target post must exist, be non-deleted, and have status `published`; otherwise the API returns `404` (same rule `createComment` uses).
- [ ] FR-003: The request body's `voteType` must be `UPVOTE` or `DOWNVOTE`; anything else is a validation/parse error.
- [ ] FR-004: Both `PUT` and `GET` responses include `postId`, `likeCount` (count of `UPVOTE`), `dislikeCount` (count of `DOWNVOTE`), and `userVote` (the caller's current vote, or `null`).
- [ ] FR-005: `GET /api/posts/{postId}/votes` requires no authentication; when a valid JWT is present, `userVote` reflects that caller's vote, otherwise it is `null`.
- [ ] FR-006: `GET` is otherwise scoped by the same post-visibility rule as `PUT` (`404` if the post isn't published/doesn't exist).

### Non-Functional Requirements

- [ ] NFR-SEC-001: `PUT` without a valid JWT returns `401 Unauthorized`.
- [ ] NFR-SEC-002: The endpoint does not accept a client-supplied `userId`; it is always derived from the JWT.
- [ ] NFR-MAINT-001: Reuses the existing `vote` table (`V8`) as-is; no migration required.

## Design overview

Add a `Vote` entity (`userId`, `postId`, `voteType` enum `UPVOTE`/`DOWNVOTE`, `createdAt`) mapping the existing table, a `VoteRepository` (`findByUserIdAndPostId`, `countByPostIdAndVoteType`), and a `VoteService` with `castVote` (insert/delete/switch per `BR-VOTE-001`) and `getVotes` (counts + optional viewer's vote). `VoteController` exposes `PUT /api/posts/{postId}/votes` (authenticated) and `GET /api/posts/{postId}/votes` (public, optionally personalized) — the first endpoint in this codebase to read an optional identity on an otherwise-public route, per `BR-VOTE-002`.

## Success metrics

All acceptance scenarios below pass in automated tests before merge.

## Acceptance criteria

**As an** authenticated user, **I want to** like or dislike a post and see the current tally, **so that** I can express sentiment and see community reaction.

- [ ] Given an authenticated user with no existing vote, when they cast `UPVOTE`, then a vote is recorded, `likeCount` increases by one, and `userVote` is `UPVOTE`.
- [ ] Given a user who already voted `UPVOTE`, when they cast `UPVOTE` again, then their vote is removed, `likeCount` decreases by one, and `userVote` is `null`.
- [ ] Given a user who already voted `UPVOTE`, when they cast `DOWNVOTE`, then their vote switches: `likeCount` decreases by one, `dislikeCount` increases by one, and `userVote` is `DOWNVOTE`.
- [ ] Given no `Authorization` header, when `GET .../votes` is called, then it still succeeds (`200 OK`) with the post's counts and `userVote: null`.
- [ ] Given a valid JWT for a user who has voted, when `GET .../votes` is called, then `userVote` reflects that user's current vote.
- [ ] Given a missing, deleted, or unpublished post, when either endpoint is called, then `404 Not Found` is returned.
- [ ] Given no `Authorization` header, when `PUT .../votes` is called, then `401 Unauthorized` is returned and no vote is recorded.

## Risks / open questions

- None outstanding for this scope. Showing vote summaries on the general post-read endpoints, if wanted later, is a separate, explicitly deferred decision (see Non-goals).

---

## Related

- API reference: `docs/apis/post/put-posts-postid-votes.md`, `docs/apis/post/get-posts-postid-votes.md`
- Business rules: `docs/brs/votes.md`
- Related feature (same deferral pattern): `docs/feats/create-comments-api.md`
