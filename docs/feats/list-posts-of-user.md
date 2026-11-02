# Feature Spec: List a Member's Posts

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Public endpoint to list a member's posts. Guests and other members see published posts; the owner and Administrators also see drafts and other non-public states.

## Problem / motivation

`GET /api/posts` only serves the authenticated user's own posts. The business rules let anyone view published content and a member's public profile, so a way to browse another member's posts is missing.

## Goals

- Anyone can list a member's published posts without logging in.
- Owner and Administrator can list all non-deleted posts of that member.

## Non-goals

- A global public feed, search, or filtering by category/type (separate tasks).
- Changing `GET /api/posts`.

## Requirements

### Functional Requirements

- [x] FR-001: `GET /api/users/{userId}/posts` is reachable without a JWT.
- [x] FR-002: Guests and other members receive only `published`, non-deleted posts.
- [x] FR-003: The owner and Administrators receive all non-deleted posts.
- [x] FR-004: Unknown or deleted users return `404`; invalid pagination returns `400`; a bad token returns `401`.
- [x] FR-005: Pagination follows BR-POST-003 defaults (page 0, size 20, max 100).

## Design overview

`UserPostController` (route permitted for GET in `SecurityConfig`) resolves the optional viewer from the authentication and passes viewer id and admin flag to `PostService.listPostsOfUser`, which chooses between the published-only query and the full owner query.

## Acceptance criteria

- [x] Given a guest, then only that user's published posts are returned.
- [x] Given the owner or an admin, then drafts and hidden posts are included.
- [x] Given another logged-in member, then only published posts are returned.
- [x] Given an unknown user id, then `404` is returned.

## Related

- API reference: `docs/apis/post/get-users-userid-posts.md`
- Business rules: `docs/brs/posts.md`
