# Feature Spec: Delete Media API (soft delete + async physical purge)

## Status

Implemented

## Author / owner

Vegalife backend team — GitHub issue #39 (parent: media lifecycle epic, alongside `upload-media-api.md`).

## Summary

Add `DELETE /api/media/{mediaId}`: the media's owner (or an administrator) marks the row deleted, the row disappears from every read path, and the stored Cloudinary object is destroyed asynchronously by the existing outbound message worker — never inside the API request.

## Problem / motivation

Media can be uploaded (`POST /api/media/upload`), confirmed (`POST /api/media/{mediaId}/confirm`) and read (`GET /api/media/{mediaId}`), but there is no way to delete any of it. A user who regrets a photo, an admin acting on a moderation decision, or a support cleanup has no endpoint at all, and every confirmed object stays on Cloudinary indefinitely — permanent storage cost for content nobody can remove. Posts keep rendering media the owner wanted gone.

## Goals

- One delete endpoint with a real access boundary: only the uploader or `ROLE_ADMIN` can delete.
- A deleted media row becomes invisible everywhere it can be read — the GET endpoint, the admin video list, and the `mediaIds` of any post that references it.
- The provider object is destroyed eventually, with the queue's retry/backoff, without the API ever waiting on Cloudinary.
- Deleting twice is a no-op, not a duplicate purge.

## Non-goals

- Hard-deleting the `media` row or any retention/sweep job over soft-deleted rows (the row is the audit trail; its `deleted_at` is what makes everything else work).
- Removing `post_media` links or the media from posts — posts keep their (now invisible) association, by design of the `ON DELETE CASCADE` schema in `V7`.
- Synchronous physical deletion inside the request, or aborting an in-flight `uploading` confirmation.
- Changing the read endpoints' ownership model: `GET /api/media/{mediaId}` stays open to any authenticated caller (BR-MEDIA-002) — only delete enforces ownership.
- Deleting comments, recipes, or any other media consumer (media is only consumed by posts today).

## Requirements

### Functional Requirements

- [x] FR-001: `DELETE /api/media/{mediaId}` sets `media.deleted_at` when the caller is the row's `uploaded_by` or holds `ROLE_ADMIN`; any other authenticated caller gets `403`, and an ID with no row gets `404`.
- [x] FR-002: Repeating the call on an already-soft-deleted row returns `200` and changes nothing — no second purge message is enqueued (idempotent).
- [x] FR-003: The soft delete and exactly one `MEDIA_PURGE` outbound message are written in the same transaction, so a deleted row is never left without a purge (and a rolled-back delete never leaves a purge behind).
- [x] FR-004: The purge message follows the existing enqueue convention (`PostService.enqueueContentFilter`): `channel = MEDIA_PURGE`, `recipient = <mediaId>`, `payload = {"mediaId": "<uuid>"}` as JSONB, `status = PENDING`, `attempts = 0`, `nextAttemptAt = now`.
- [x] FR-005: `MediaPurgeOutboundAdapter` implements the existing `OutboundChannelAdapter` seam: it loads the media row by the payload's `mediaId` and calls `PresignedUploadProvider.destroy(external_id)`. A row with no `external_id` (upload never completed) or a provider report that the object is already gone completes the message without retrying; a provider failure propagates so the queue applies its normal backoff and terminal handling.
- [x] FR-006: All read paths exclude soft-deleted media: `GET /api/media/{mediaId}` returns `404`, `GET /api/admin/videos` excludes the row (`MediaSpecifications` already filters `deleted_at IS NULL`), and both `PostMapper.mediaIds` and `AdminPostMapper.mediaIds` omit media whose `deleted_at` is set, so no post response — user-facing or admin — advertises a deleted ID.
- [x] FR-007: Migration `V24` widens the `chk_outbound_message_channel` CHECK (created in `V15`, precedent `V19`) to admit `MEDIA_PURGE` alongside `EMAIL`.

### Non-Functional Requirements

- [x] NFR-SEC-001: The destructive operation is the first media endpoint with an access boundary: non-owner, non-admin callers receive `403`, and the ownership decision uses only `media.uploaded_by` against the JWT principal (BR-MEDIA-002's recorded, previously unenforced owner).
- [x] NFR-MAINT-001: No new infrastructure — the purge reuses `outbound_message`, `OutboundMessageQueueDao`, and `OutboundMessageDrainer` exactly as email and content filtering do; the only new class in the delivery path is the adapter.
- [x] NFR-SCALE-001: `DELETE` latency is independent of provider availability: Cloudinary being unreachable degrades only the queue (retries), never the API response.

## Design overview

Reuses the outbox already in place for email and post content filtering (ADR-005). The API transaction writes `media.deleted_at` plus one `outbound_message` row (`channel = MEDIA_PURGE`); the existing 5-second `OutboundMessageDrainer` claims it, resolves `MediaPurgeOutboundAdapter` by channel, and destroys the object at `media.external_id` via a new `PresignedUploadProvider.destroy`, completing the message (`payload` nulled by the queue's terminal transition).

```
DELETE /api/media/{id} ──▶ MediaService.deleteMedia (one tx)
                             ├─ media.deleted_at = now   (owner-or-admin check first)
                             └─ outbox: MEDIA_PURGE / recipient=mediaId / payload={mediaId}
                                        │
        OutboundMessageDrainer (5s) ◀──┘
             └─▶ MediaPurgeOutboundAdapter.deliver
                    └─▶ provider.destroy(media.external_id)  → COMPLETED (or retry/backoff)
```

Components touched: `MediaController` (new endpoint), `MediaService` (delete + enqueue), `PresignedUploadProvider`/`CloudinaryUploadProvider` (`destroy`), new `MediaPurgeOutboundAdapter`, `OutboundChannel.MEDIA_PURGE`, `V24` migration, `PostMapper`/`AdminPostMapper` `mediaIds` filter, and a new `ForbiddenException` + `GlobalExceptionHandler` entry (the repo currently has no 403 path — `deletePost` answers 404 for non-owners, and Spring's `AccessDeniedException` would fall into the catch-all `Exception.class` handler and surface as 500).

## Success metrics

- After a successful `DELETE`, an immediate `GET /api/media/{mediaId}` returns `404` for 100% of deletes (integration test).
- With the provider reachable, every enqueued `MEDIA_PURGE` message reaches `COMPLETED` within a few drainer poll cycles (5s poll, 60s visibility timeout).
- Repeated deletes of the same media produce exactly one non-terminal purge message per row (asserted in tests).

## Acceptance criteria

**As a** media owner or administrator, **I want to** delete a media record so that it disappears from the platform and its stored file is cleaned up without me waiting for that cleanup.

- [x] Given a confirmed media row, when its owner calls `DELETE /api/media/{mediaId}`, then the row is soft-deleted, one purge message is enqueued, and the response is `200`.
- [x] Given a confirmed media row, when an administrator calls `DELETE /api/media/{mediaId}`, then it behaves exactly as for the owner.
- [x] Given a media row, when a caller who is neither the uploader nor an admin calls delete, then the response is `403` and nothing is written.
- [x] Given an ID with no media row, when delete is called, then the response is `404`.
- [x] Given an already-deleted media row, when delete is called again, then the response is `200` and no second purge message exists.
- [x] Given a soft-deleted media, when the GET endpoint, admin video list, or any post response is read, then the media does not appear (`404` / excluded / absent from `mediaIds`).
- [x] Given an enqueued purge message, when the drainer delivers it, then the provider object at `external_id` is destroyed and the message completes; when the provider errors, then the message retries with backoff and eventually goes terminal while the soft delete stays committed.

## Risks / open questions

- **Post edits referencing deleted media**: `PostService.resolveMedia` already looks up `findByIdInAndDeletedAtIsNull`, so re-submitting a deleted `mediaId` on edit returns `404 Media not found`. That is arguably correct (you cannot re-attach deleted media), but a client that resends its previous payload unchanged will now fail where it used to succeed. Worth a frontend heads-up; no backend change planned.
- **Orphaned purge messages**: if a media row is hard-deleted later (a future retention job, out of scope here), a pending purge message must not blow up — the adapter treats a missing row as success. Planned for in FR-005.
- **Deletes during an in-flight confirm**: a concurrent `POST /{mediaId}/confirm` could resurrect metadata on a row that is already `deleted_at IS NOT NULL`. The row stays invisible either way; not worth a lock for this phase, flagged as accepted.
- Physical purge reliability depends on the drainer running in the deployed environment (same operational dependency as outbound email — see `docs/feats/outbound-message-queue.md`).
