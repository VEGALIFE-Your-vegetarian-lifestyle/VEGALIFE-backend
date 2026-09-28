# Feature Spec: Hide a Post (Moderation)

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Let an Administrator hide any post from the platform and lift the hide, with every action recorded (BR-ADMIN-002).

## Problem / motivation

Administrators need to take problematic content out of circulation without deleting it, and to reverse that decision later. The `hidden` status already exists but had no way to be set.

## Goals

- Admin can hide and unhide any non-deleted post.
- Hide/unhide is auditable through `moderation_log`.

## Non-goals

- Owners hiding their own posts (they use `publish: false`, BR-CONTENT-003).
- Hiding comments or videos separately; semantic filtering (deferred); a public feed.

## Requirements

### Functional Requirements

- [x] FR-001: `PATCH /api/posts/{postId}/visibility` with `{"hidden": boolean}` is available to `ADMIN` only (`403` otherwise, `401` without JWT).
- [x] FR-002: `hidden: true` sets status `hidden` and clears `publishedAt`; repeating it is a no-op.
- [x] FR-003: `hidden: false` moves a hidden post to `created`; on a post that is not hidden it returns `400`.
- [x] FR-004: Missing or soft-deleted posts return `404`.
- [x] FR-005: Each state change writes `HIDE_POST` or `UNHIDE_POST` to `moderation_log`.
- [x] FR-006: The owner cannot publish a hidden post through the edit endpoint.

## Design overview

`SecurityConfig` restricts the route to `ROLE_ADMIN`. `PostController.updateVisibility` calls `PostService.updateVisibility`, which loads any non-deleted post, updates status, and always logs the action. No migration is needed.

## Acceptance criteria

- [x] Given an admin and a published post, when hidden is true, then status is `hidden`, `publishedAt` is null and a log entry exists.
- [x] Given a hidden post, when the owner sends `publish: true`, then `400` is returned.
- [x] Given a non-admin JWT, then `403` is returned and the post is unchanged.

## Related

- API reference: `docs/apis/post/patch-posts-postid-visibility.md`
- Business rules: `docs/brs/posts.md`
