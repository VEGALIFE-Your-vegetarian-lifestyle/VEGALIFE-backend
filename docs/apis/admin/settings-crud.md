# API Reference: Admin Platform Settings

## Overview

Admin-configurable platform settings, grouped by category (issue #13; follow-up revision). Eight keys in three categories — `media`, `content`, `ai` — each backed by a real business rule and read live by its consumer on every request/decision (no cache, no snapshot at startup). Every change is audited via the existing `moderation_log` table, and the row itself records who last changed it.

## Endpoints

```text
GET   /api/admin/settings
PATCH /api/admin/settings
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## GET /api/admin/settings

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Settings retrieved successfully",
  "data": [
    {
      "category": "ai",
      "items": [
        { "key": "ai_chat_enabled", "value": "true", "updatedAt": "2026-01-01T00:00:00Z" }
      ]
    },
    {
      "category": "content",
      "items": [
        { "key": "content_filter_accept_threshold", "value": "0.70", "updatedAt": "2026-01-01T00:00:00Z" },
        { "key": "content_filter_enabled", "value": "true", "updatedAt": "2026-01-01T00:00:00Z" },
        { "key": "content_filter_reject_threshold", "value": "0.45", "updatedAt": "2026-01-01T00:00:00Z" }
      ]
    },
    {
      "category": "media",
      "items": [
        { "key": "max_image_upload_mb", "value": "5", "updatedAt": "2026-01-01T00:00:00Z" },
        { "key": "max_video_upload_mb", "value": "50", "updatedAt": "2026-01-01T00:00:00Z" },
        { "key": "upload_allowed_image_types", "value": "image/jpeg,image/png,image/webp", "updatedAt": "2026-01-01T00:00:00Z" },
        { "key": "upload_allowed_video_types", "value": "video/mp4,video/webm", "updatedAt": "2026-01-01T00:00:00Z" }
      ]
    }
  ]
}
```

Values are always strings; each key's type and bounds are fixed in a code-level registry (`SettingKey`), not stored as separate columns, so there is one source of truth for parsing.

## PATCH /api/admin/settings

### Request Body

```json
{ "settings": { "max_image_upload_mb": "10" } }
```

One or more key/value pairs. At least one must be supplied; every key must be a known setting, and every value must satisfy that key's type and bounds (see **Seeded settings** below).

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 200 | Applied | `Settings updated successfully` (returns the full updated settings, same shape as `GET`) |
| 400 | Empty `settings` map, any key is not a known setting, or any value fails its type/bounds check (including `content_filter_reject_threshold` not being less than `content_filter_accept_threshold`) | `Validation failed` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |

Nothing is persisted for a batch if any key or value in it is invalid (the whole request is validated before anything is written).

## Seeded settings

| Category | Key | Type | Default | Bounds | Meaning | Consumer |
|----------|-----|------|---------|--------|---------|----------|
| media | upload_allowed_image_types | comma list | `image/jpeg,image/png,image/webp` | non-empty, subset of known image types | Accepted image content types for upload. | `MediaService`, `CloudinaryUploadProvider` |
| media | upload_allowed_video_types | comma list | `video/mp4,video/webm` | non-empty, subset of known video types | Accepted video content types for upload. | same |
| media | max_image_upload_mb | int | `5` | 1–100 | Maximum accepted image upload size, in megabytes. | same |
| media | max_video_upload_mb | int | `50` | 1–1024 | Maximum accepted video upload size, in megabytes. | same |
| content | content_filter_enabled | boolean | `true` | — | When false, a published post skips the semantic filter and lands directly in the admin review queue. | `PostService`, `PendingFilterSweepJob` |
| content | content_filter_accept_threshold | decimal | `0.70` | 0.0–1.0 | Relevance score at or above which a post passes the filter. | `EmbeddingRelevanceScorer` |
| content | content_filter_reject_threshold | decimal | `0.45` | 0.0–1.0, must be less than the accept threshold | Relevance score below which a post is rejected. | same |
| ai | ai_chat_enabled | boolean | `true` | — | When false, AI chat turns (send/stream) are refused with 503; reading existing conversations is unaffected. | `AiChatService` |

The category set is `media` / `content` / `ai` — the `auth` and `limits` categories from the original seed no longer have any member.

## Business Rules

- Only an Administrator may view or change settings.
- Every successful change writes a `moderation_log` row: `action = UPDATE_SETTING`, `targetType = SETTING`, `targetId` (the setting's id), `reason` (`"<key>: '<old>' -> '<new>'"`) — who/what/when (BR-ADMIN-002's audit convention). The `platform_setting.updated_by` column additionally names the admin on the row itself.
- Every consumer reads its setting(s) live, per request or per scheduled decision — never cached, never snapshotted at startup — so a PATCH takes effect on the very next request. A missing row, a blank value, or a value that fails validation falls back to the registry default with a WARN log rather than failing the request; only a write (PATCH) rejects an invalid value.
- Defaults are byte-identical to the `application.yml` values they replace, so a deployment with every setting at its default behaves exactly as it did before this table existed.

## Non-goals

A settings UI (frontend) and environment-specific configs are out of scope (issue #13 non-goals).

## Related

- Shared error/response contract: `docs/apis/error-responses.md`
- Business rules: `docs/brs/posts.md` (BR-FILTER-004/005), `docs/brs/ai.md` (BR-AI-007)
