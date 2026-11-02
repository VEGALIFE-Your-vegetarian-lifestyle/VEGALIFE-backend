# Feature Spec: Post Schema — Rich-Text raw_content, Drop type/video_url

## Status

Implemented

## Author / owner

Vegalife backend team

## Summary

Replace the legacy blog/video post split with a single rich-text post format: add `post.raw_content` (JSONB) to store the frontend editor's rich-text document, keep plain `content` only as the semantic-filtering input, and remove `type`, `video_url`, and the request-level `mediaId` entirely.

## Problem / motivation

Posts are authored in the frontend's rich-text editor, which produces a JSON document, but the API only accepted and returned plain text, so formatting was lost end-to-end. The `type` (blog/video) split and `video_url` from migration `V17` are legacy — posts are one rich-text format now.

## Goals

- Add `raw_content` to the post schema and expose it on create/update/list responses.
- Keep plain `content` as the only input to semantic filtering (BR-FILTER-004/005).
- Remove `type`, `video_url`, and request-level `mediaId` from the post request/response contracts and the service logic that validated them.

## Non-goals

- Converting existing plain `content` into a rich-text document; pre-existing rows backfill to `{}`.
- Removing post↔media linkage (`post_media`, `mediaIds` responses, `AdminVideoService` grouping) — only the request-level `mediaId` used to attach media at create/update time is removed.
- Any change to content-filter semantics, thresholds, or the outbound queue.
- Frontend/editor work.

## Requirements

### Functional Requirements

- [x] FR-001: Migration `V27__add_post_raw_content_drop_type_video_url.sql` adds `post.raw_content JSONB NOT NULL DEFAULT '{}'` (backfilling existing rows to `{}`) and drops `type` and `video_url`. (The issue's acceptance criteria name this migration "V23"; that version was already used by `V23__make_user_profile_fields_nullable.sql`, so the next free version, V27, is used instead.)
- [x] FR-002: `PostCreateRequest` and `PostUpdateRequest` require both `content` (non-blank plain text) and `rawContent` (a JSON object); `type`, `videoUrl`, and `mediaId` are removed from both.
- [x] FR-003: `PostListResponse` returns `rawContent` and no longer returns `content`, `type`, or `videoUrl`.
- [x] FR-004: `AdminPostListResponse` drops `type` and `videoUrl` but keeps plain `content` for moderation review.
- [x] FR-005: All type/video business logic is removed from `PostService` (`validateTypeSpecificFields`, the type-immutability and blog/video checks in `updatePost`, the video-publish-requirement check in `applyPublishState`, the media-changed check in `isContentChanged`) while `post_media`/`mediaIds` stay untouched.
- [x] FR-006: Enqueue/re-queue decisions for content filtering are unchanged and the filter payload still reads plain `content`; a `rawContent`-only change never re-queues.

## Design overview

`Post.rawContent` is mapped as `com.fasterxml.jackson.databind.JsonNode` with `@JdbcTypeCode(SqlTypes.JSON)`, matching the existing `OutboundMessage.payload` JSONB pattern in spirit but using `JsonNode` directly (no manual (de)serialization) since both the entity and the DTOs need the same JSON-object shape. MapStruct maps `rawContent` by field name on both `PostMapper` and `AdminPostMapper` without extra `@Mapping` annotations. `PostUpdateRequest` stays a partial update for `title`, `featuredImageUrl`, `categoryIds`, and `publish`, but `content`/`rawContent` are mandatory on every call (per the issue's literal acceptance criterion), so `isUpdateRequestNotEmpty()` is removed — the request can no longer be empty.

## Success metrics

All automated acceptance scenarios for the new schema and request/response contracts pass before merge; no regression in content-filter enqueue/re-queue behavior.

## Acceptance criteria

**As a** frontend client, **I want to** send and receive the post's rich-text document, **so that** formatting survives round-trips through the API.

- [x] Given a create or update request, when `content` or `rawContent` is missing, blank, or `rawContent` is not a JSON object, then the API returns `400 Bad Request`.
- [x] Given a valid create or update, when the post is saved, then `raw_content` is persisted and returned as `rawContent` in the response; `content`, `type`, and `videoUrl` are not present in `PostListResponse`.
- [x] Given a post is created or edited, when deciding whether to (re-)queue content filtering, then only title/plain-content changes and explicit `publish` are considered; a `rawContent`-only change does not re-queue.
- [x] Given the admin post list, when posts are returned, then `type`/`videoUrl` are absent and plain `content` is still present for moderation.
- [x] Given an existing database, when migration `V27` runs, then all pre-existing rows get `raw_content = '{}'` and `type`/`video_url` are dropped without error.

## Risks / open questions

- The issue names the migration "V23"; this repo already has a `V23` migration for an unrelated change, so `V27` is used instead (documented in the migration file header and the PR description).
- Removing request-level `mediaId` means new posts can no longer attach media at create/update time through this endpoint; existing `post_media` rows and `AdminVideoService` grouping are unaffected, but attaching new media to a post has no replacement endpoint as of this change (out of scope per the issue's non-goals).

---

## Related

- API references: `docs/apis/post/post-posts.md`, `docs/apis/post/patch-posts-postid.md`, `docs/apis/post/get-posts.md`, `docs/apis/admin/get-posts.md`
- Business rules: `docs/brs/posts.md` (BR-POST-004, BR-POST-005, BR-POST-007)
- Data dictionary: `docs/arch/data-dictionary.md` (Table 4: Post)
- Migration: `src/main/resources/db/migration/V27__add_post_raw_content_drop_type_video_url.sql`
