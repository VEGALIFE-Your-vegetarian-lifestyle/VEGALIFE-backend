# Feature Spec: List All User Videos API (Admin)

## Status
In progress

## Author / owner
Backend team; driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/3

## Summary
Allow admin users to retrieve a paginated list of every non-deleted uploaded video on the platform — across all uploaders and all upload states — filterable by status, uploader, and creation date range, with each item carrying its video metadata and the posts it is attached to.

## Problem / motivation
Admins review video content for policy violations (issue #3, Content Moderation epic, Sprint 2). The only media read endpoint today is `GET /api/media/{mediaId}`, which fetches one record by id and requires the caller to already know that id; there is no way to browse uploaded videos. Videos are `media` rows (migration `V4__create_media_table.sql`), not a `Video` entity, and `media` also holds images, so nothing in the API exposes "all video uploads" as a set. Without it, moderation of video uploads requires database access.

## Goals
- Admins can page through every non-deleted video upload regardless of uploader or upload status, with uploader identity, the video's technical metadata, and the posts the video is attached to on each row.
- Admins can narrow the list by upload status, uploader, and `createdAt` range.
- Non-admin authenticated users are denied access (403); anonymous callers get 401.

## Non-goals
- Video playback or transcoding in admin (issue #3 non-goal).
- Automated video analysis or thumbnail generation (issue #3 non-goal).
- Listing image uploads — the endpoint returns video MIME types only.
- Upload, confirmation, or deletion of media (already covered by `docs/feats/upload-media-api.md`).
- Moderation actions on a video or its posts (bulk actions are issue #5).
- Soft-deleted media visibility (`deletedAt IS NULL` always).
- Filtering by file size, duration, or MIME subtype (`video/mp4` vs `video/webm`).

## Requirements

### Functional Requirements
- [ ] FR-001: `GET /api/admin/videos` returns a paginated list of video uploads to callers with role ADMIN.
- [ ] FR-002: Only non-deleted `media` rows whose `mimeType` is a video type are returned; image rows are never returned.
- [ ] FR-003: The result spans all uploaders and all `Media.Status` values (`uploading`, `succeed`, `failed`).
- [ ] FR-004: Each item includes `id`, `mediaUrl`, `thumbnailUrl`, `description`, `status`, `durationSeconds`, `fileSizeBytes`, `mimeType`, `width`, `height`, `externalId`, `createdAt`, `updatedAt`, plus uploader `userId`, `username`, `email`, plus `posts` — the non-deleted posts the video is attached to (each with `id`, `title`, `status`), empty when the video is not yet attached to any post.
- [ ] FR-005: Optional query filters: `status` (Media.Status), `userId` (UUID uploader), `createdFrom`, `createdTo` (ISO-8601 datetime on `createdAt`).
- [ ] FR-006: Pagination via `page` (0-based, default 0), `size` (default 20, max 100), optional `sort` (default `createdAt,desc`); sort property must be one of `createdAt`, `updatedAt`, `durationSeconds`, `fileSizeBytes`, otherwise 400.
- [ ] FR-007: Soft-deleted media (`deletedAt != null`) is never returned.
- [ ] FR-008: Non-admin authenticated requests receive 403; missing/invalid JWT receives 401.
- [ ] FR-009: `createdFrom` after `createdTo` is rejected with 400.
- [ ] FR-010: Associated posts are resolved for the whole page in one query, not one query per row.

### Non-Functional Requirements
- [ ] NFR-SEC-001: Endpoint path `/api/admin/**` is restricted to `ROLE_ADMIN` by the existing security filter chain rule.
- [ ] NFR-SEC-002: The response DTO exposes no storage credentials or provider tokens; `externalId` is the provider object key already returned by `GET /api/media/{mediaId}`.
- [ ] NFR-MAINT-001: Response wrapped in `ApiResponse<T>` with pagination shape from `shared/dto/PageResponse`.
- [ ] NFR-MAINT-002: Controller stays thin; filter parsing and page assembly live in a service; predicates live in a `MediaSpecifications` class alongside `PostSpecifications` / `UserSpecifications`.
- [ ] NFR-SCALE-001: A page of N videos costs 1 page query + 1 associated-posts query + at most N lazy uploader reads — no unbounded per-row query fan-out.

## Design overview
New `AdminVideoController` (`controller/admin`) and `AdminVideoService` (`service/admin`) reached through the `GET /api/admin/videos` path already covered by `SecurityConfig`'s `/api/admin/**` → `hasRole("ADMIN")` matcher, so no security configuration change and no migration — `media` (V4, extended by V20) and `post_media` already exist.

`MediaRepository` gains `JpaSpecificationExecutor<Media>` and a new `MediaSpecifications.allVideosWithFilters` mirroring `PostSpecifications.allWithFilters`: soft-delete exclusion plus an unconditional `mimeType LIKE 'video/%'` predicate (the upload allowlist in `docs/brs/media.md` permits only `video/mp4` and `video/webm`, and the prefix predicate keeps working if another video type is added) plus optional status/uploader/date predicates. Filter parsing, the `createdFrom`/`createdTo` order check, and the sort allowlist are copied from `AdminPostService`, with one deliberate narrowing — the status enum is `Media.Status`, and the sortable set is `createdAt`, `updatedAt`, `durationSeconds`, `fileSizeBytes` (no `title`/`viewCount`/`publishedAt` on a media row).

`Post.media` is a one-directional `@ManyToMany` through `post_media`, so associated posts are resolved by one `PostRepository` query over the page's media ids and grouped onto the mapped responses, rather than adding an inverse collection to the `Media` entity. Mapping goes through a MapStruct `AdminVideoMapper` that ignores `posts`; the service fills them.

## Success metrics
- Issue #3's four acceptance criteria are covered by unit tests plus an integration test exercising the endpoint end to end before PR merge.
- Endpoint returns a 20-row page in at most 3 SQL round trips (page, posts, no per-row post queries).

## Acceptance criteria
**As an** admin, **I want to** list and filter every video uploaded on the platform, **so that** I can review video content for policy violations without database access.

- [ ] Given an admin JWT, when requesting `GET /api/admin/videos`, then 200 with a paginated list of video uploads is returned.
- [ ] Given videos uploaded by several users in mixed statuses, when requesting the endpoint, then videos from all users and all statuses are returned, and no image rows appear.
- [ ] Given an admin JWT, when filtering by `status`, `userId`, or `createdFrom`/`createdTo`, then only matching videos are returned.
- [ ] Given a video attached to one or more posts, when inspecting its item, then those posts appear with id, title, and status; an unattached video shows an empty `posts` array.
- [ ] Given a non-admin JWT, when requesting the endpoint, then 403 is returned.
- [ ] Given no JWT, when requesting the endpoint, then 401 is returned.

## Risks / open questions
- The 403 body is Spring Boot's default error shape (no custom `AccessDeniedHandler` exists), same as every other admin endpoint today.
- `uploaded_by` is nullable (V20) — rows created before V20 or written outside `MediaService.createGrant` could have no uploader, in which case the uploader fields are null. Acceptable: the filter simply will not match them.
- Uploader mapping reads a lazy `uploadedBy` per row, bounded by `size` (max 100) per page. Same trade-off already accepted in `docs/feats/list-all-posts-admin-api.md`; an `@EntityGraph` is the fix if it shows up in profiling.
- Soft-deleted media stays invisible to admins, consistent with the posts and users admin lists.
