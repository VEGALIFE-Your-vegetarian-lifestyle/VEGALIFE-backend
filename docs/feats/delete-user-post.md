# Feature Spec: Delete a User Post

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Allow the owner of a post, or an Administrator, to delete it (soft delete), following BR-CONTENT-001 and BR-ADMIN-002.

## Problem / motivation

Users can create, list, and edit posts but cannot remove content they no longer want, and administrators cannot remove content during moderation.

## Goals

- Let a signed-in user delete their own non-deleted post.
- Let an Administrator remove any non-deleted post and keep a record of it.

## Non-goals

- Restoring deleted posts, hard deleting rows, or bulk deletion.
- Deleting comments or votes on the post (no cascade behavior is changed).
- Semantic filtering (deferred).

## Requirements

### Functional Requirements

- [x] FR-001: `DELETE /api/posts/{postId}` requires a valid JWT.
- [x] FR-002: The owner can delete their post; a non-admin receives `404` for missing, already-deleted, or other users' posts.
- [x] FR-003: An Administrator can delete any non-deleted post.
- [x] FR-004: Deletion sets `deleted_at`; the post disappears from the owner's list and cannot be edited or deleted again.
- [x] FR-005: An Administrator deleting another user's post writes a `moderation_log` entry (`DELETE_POST`, actor, target, time).

### Non-Functional Requirements

- [x] NFR-SEC-001: Authorization is derived from the JWT principal and role, never from client input.

## Design overview

`PostController.deletePost` passes the principal and an `isAdmin` flag to `PostService.deletePost`, which shares `findManageablePost` and the moderation-log helper with `updatePost`. Admins look posts up by id and non-deleted state; others by id, owner, and non-deleted state.

## Acceptance criteria

- [x] Given an owned non-deleted post, when the owner deletes it, then `200 OK` is returned and `deleted_at` is set.
- [x] Given a deleted, missing, or other user's post, when a non-admin deletes it, then `404 Not Found` is returned and nothing changes.
- [x] Given a missing/invalid JWT, then `401 Unauthorized` is returned.
- [x] Given an Administrator deleting another user's post, then the post is soft-deleted and a `DELETE_POST` log entry is stored.

## Related

- API reference: `docs/apis/post/delete-posts-postid.md`
- Business rules: `docs/brs/posts.md`
