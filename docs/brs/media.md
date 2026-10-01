# Business Rules: Media Upload

Constraints governing uploaded media, from grant issuance through confirmation. ID format `BR-MEDIA-<NNN>`.

---

# Business Rule: Media Content-Type Allowlist

## Rule ID
`BR-MEDIA-001`

## Status
Active

## Statement
A media upload grant may only be requested for one of exactly these MIME types: `image/jpeg`, `image/png`, `image/webp`, `video/mp4`, `video/webm`. Any other `contentType` — including absent, blank, uppercase-variant or wildcard types such as `image/*` — is rejected with `400` before a `media` row is created. The same allowlist governs whether the real object is accepted at confirmation.

## Rationale
The platform renders user-supplied images and video in the feed. Unrestricted formats would permit SVG, HEIC, archive or executable payloads that bypass rendering assumptions and expand the attack surface for stored-XSS and content-type sniffing, while also being formats the delivery CDN cannot serve consistently.

## Scope & Exceptions
Applies to `POST /api/media/upload` and to `POST /api/media/{mediaId}/confirm`. No exceptions for any role, including administrators. The allowlist lives in configuration, not in code, so adding a format is a configuration change — but the set must never be empty or unrestricted.

## Enforcement
- `MediaProperties.upload.allowedImageTypes` / `allowedVideoTypes` — configuration under `app.media.upload.*`
- `MediaService` validates the declared `contentType` on grant creation and re-validates the provider-reported MIME type on confirmation
- Provider-side: `allowed_formats` is included in the signed upload fields so non-conforming files are rejected at upload time
- API: `400 Unsupported content type: <value>`

## Last Reviewed
2026-09-30, by Vegalife backend team

---

# Business Rule: Media Ownership Is Derived from Authentication

## Rule ID
`BR-MEDIA-002`

## Status
Active

## Statement
The owner of a media row is the user identified by the authenticated JWT at grant-creation time. Ownership is never taken from a request field. Confirmation of an upload requires a valid JWT; a caller cannot confirm a grant they did not initiate by supplying a user identifier.

## Rationale
Ownership attribution must be unforgeable. If the client could declare the owner, an attacker could attribute uploads to another account, corrupting the audit trail that `media.uploaded_by` exists to provide (acceptance criterion: media is linked to a user).

## Scope & Exceptions
Applies to all three media endpoints. Deliberate exception: ownership is **recorded but not enforced as an access boundary** — the product decision for this feature is that attaching another user's media to one's own post is acceptable, so no endpoint returns `403` on the basis of `uploaded_by`. See the feature spec's risks section.

## Enforcement
- `MediaController` extracts the principal from `SecurityContext`; no user ID is accepted in the request DTO
- `MediaService` persists `media.uploaded_by` from that principal
- Migration `V20__add_media_upload_lifecycle.sql` adds the `uploaded_by` column
- API: `401 Unauthorized` when no valid JWT is present

## Last Reviewed
2026-09-30, by Vegalife backend team

---

# Business Rule: Per-Class Upload Size Ceilings

## Rule ID
`BR-MEDIA-003`

## Status
Active

## Statement
Images are limited to 5 MB (`5242880` bytes) and videos to 50 MB (`52428800` bytes) by default, both configurable under `app.media.upload`. The ceiling is checked twice: against the client's declared `sizeBytes` at grant creation, and against the **provider-reported** actual byte count at confirmation. The confirmation check is authoritative; a client that under-declares `sizeBytes` still cannot get an oversized object accepted.

## Rationale
The declared size at grant time is a cheap early rejection that avoids creating rows and contacting the provider. It is not trustworthy — it is attacker-controlled — so the real enforcement happens after read-back, when the actual byte count is known. Without the second check the limit would be advisory only.

## Scope & Exceptions
Applies to `POST /api/media/upload` and `POST /api/media/{mediaId}/confirm`. The class (image vs video) is determined by the media row's MIME type, so a video uploaded under an image grant is measured against the image ceiling. `sizeBytes` is optional: when omitted at grant creation, only the confirmation check applies.

## Enforcement
- `MediaProperties.upload.maxImageBytes` / `maxVideoBytes` — configuration under `app.media.upload.*`
- `MediaService` rejects declared oversize before creating a row, and rejects reported oversize at confirmation, setting `status = failed`
- Provider-side: none. Cloudinary ignores `max_file_size` at upload time (verified 2026-10-02: a grant signed with `max_file_size=1` accepted a 70-byte file) and excludes it from signature verification, so the value returned in `fields` is advisory only. The confirmation read-back above is the sole size enforcement.
- API: `400 File exceeds the 5 MB image limit` / `400 File exceeds the 50 MB limit`

## Last Reviewed
2026-10-02, by Vegalife backend team

---

# Business Rule: Object Keys Are Server-Assigned

## Rule ID
`BR-MEDIA-004`

## Status
Active

## Statement
The storage object key (`public_id`) is derived by the server from the media ID and the owner's user ID, in the form `vegalife/{userId}/{mediaId}`. The client cannot choose, override or influence it, and it is not derived from `fileName`.

## Rationale
Letting the client choose the key would enable overwrite of other users' objects, path traversal into other prefixes, and collisions. Deriving from two server-generated UUIDs guarantees uniqueness and makes the ownership prefix verifiable. `fileName` is advisory precisely because it is attacker-controlled.

## Scope & Exceptions
Applies to `POST /api/media/upload`. The key is stored on the row as `media.external_id` when confirmed, so confirmation never depends on re-deriving the naming convention.

## Enforcement
- `MediaService` builds the key from the authenticated principal and the generated media ID
- `MediaProperties` supplies the `vegalife/` namespace prefix
- API: the key appears only in the signed `fields.public_id`; it is never read from the request body

## Last Reviewed
2026-09-30, by Vegalife backend team

---

# Business Rule: Upload Grants Expire

## Rule ID
`BR-MEDIA-005`

## Status
Active

## Statement
An upload grant is confirmable for a fixed window after issuance — 15 minutes by default, configurable as `app.media.upload.grant-ttl`. Confirmation attempted after that instant returns `400 Upload grant has expired`. The expiry is recorded as `expiresAt` in the grant response so the client knows its deadline.

## Rationale
An unbounded window means a half-finished `uploading` row sits in the database indefinitely and keeps a signed credential in circulation. A short window bounds both. It is a liveness control, not a security control — the signature itself is separate.

## Scope & Exceptions
Applies to `POST /api/media/{mediaId}/confirm`. Expired grants do not transition the row to `failed`; the row simply stays `uploading` and becomes unconfirmable, so an expiry can never be mistaken for an upload failure.

## Enforcement
- `MediaProperties.upload.grantTtl` — configuration under `app.media.upload.*`
- `MediaService` compares confirmation time against the row's creation time plus the TTL
- API: `400 Upload grant has expired`

## Last Reviewed
2026-09-30, by Vegalife backend team

---

# Business Rule: Flyway Out-of-Order Enabled for Parallel Migration Branches

## Rule ID
`BR-MEDIA-006`

## Status
Active

## Statement
`spring.flyway.out-of-order` is set to `true`. Migrations are still **authored** against the next unused version number, but a migration whose version is lower than one already applied may be applied late instead of being skipped.

## Rationale
Feature branches are developed in parallel and each picks its version number when it branches. Without this setting, whichever branch merges second has its migration silently skipped — `V19__add_post_filtering.sql` arriving after `V20` would leave `post.flag` missing while `flyway validate` fails on every startup, and recovery would require a manual `flyway repair` in production. This branch specifically carries `V20` while `V19` is still in an open PR.

## Scope & Exceptions
Applies to the whole repository. Safety condition relied on here: `V19` (table `post`) and `V20` (table `media`) touch **disjoint tables**, so application order between them cannot produce a wrong schema. On a fresh database both are present and Flyway applies them in normal version order — out-of-order only engages for databases that applied `V20` first.

## Enforcement
- `src/main/resources/application.yml` → `spring.flyway.out-of-order: true`
- Process: authors must still pick the next free version number; this setting is a safety net for parallel merges, not licence to reuse a number. Two migrations with the **same** version still fail validation with a checksum mismatch.
- Note: not yet implemented when this rule was written.

## Last Reviewed
2026-09-30, by Vegalife backend team

---

# Business Rule: Confirmation Is Exactly Once

## Rule ID
`BR-MEDIA-007`

## Status
Active

## Statement
A media row may be confirmed at most once. Confirmation on a row already in status `succeed` returns `409 Media upload has already been confirmed` and changes nothing. A row in status `failed` can never be confirmed.

## Rationale
Confirmation is the transition that makes a media record usable by `PostService.resolveMedia()`. Allowing it to run repeatedly would let a later provider state overwrite an already-attached media's URL — a post's video could silently change after publication. It also makes the write non-idempotent in a way callers would not expect.

## Scope & Exceptions
Applies to `POST /api/media/{mediaId}/confirm`. A row in `uploading` whose grant has expired is *not* failed — it is simply unconfirmable — so it does not fall under this rule.

## Enforcement
- `MediaService` checks `status` before contacting the provider; `succeed` short-circuits to `409`
- The status transition `uploading → succeed` / `uploading → failed` is the only permitted movement
- API: `409 Media upload has already been confirmed`

## Last Reviewed
2026-09-30, by Vegalife backend team

---

# Business Rule: Confirmation Trusts Only Provider Read-Back

## Rule ID
`BR-MEDIA-008`

## Status
Active

## Statement
Every value persisted at confirmation — delivery URL, actual byte count, MIME type, dimensions, duration — comes from a provider read-back of the stored object key. No such value is accepted from the request. If the provider reports no object under the key, confirmation returns `400 Upload verification failed` and the row stays `uploading`.

## Rationale
Confirmation is the only point at which untrusted bytes become trusted data. Accepting a client-supplied URL or size would let a caller mark a row `succeed` without uploading anything, or attach a URL they control as a post's media — stored-XSS by way of an unvalidated `mediaUrl`. Read-back makes the provider the sole authority on what actually exists.

## Scope & Exceptions
Applies to `POST /api/media/{mediaId}/confirm`. The confirmation request body is empty by design; there are no fields to trust.

## Enforcement
- `PresignedUploadProvider.verify(...)` returns the provider's own report of existence, bytes, format, dimensions and delivery URL
- `CloudinaryUploadProvider` calls the provider's Admin API for the stored `external_id`
- The key itself comes from the stored row, never from the request
- API: `400 Upload verification failed`

## Last Reviewed
2026-09-30, by Vegalife backend team
