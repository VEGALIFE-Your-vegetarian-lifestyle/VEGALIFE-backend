# API Reference: Admin Platform Settings

## Overview

Admin-configurable platform settings, grouped by category (issue #13). A small, concrete seeded set — registration gating, an auto-moderation threshold, an AI plan limit override, and a max upload size — stored as key/value rows. Every change is audited via the existing `moderation_log` table.

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
        { "key": "default_monthly_limit_override", "value": "", "updatedAt": "2026-01-01T00:00:00Z" }
      ]
    },
    {
      "category": "auth",
      "items": [
        { "key": "registration_open", "value": "true", "updatedAt": "2026-01-01T00:00:00Z" }
      ]
    },
    {
      "category": "content",
      "items": [
        { "key": "auto_moderation_threshold", "value": "0.5", "updatedAt": "2026-01-01T00:00:00Z" }
      ]
    },
    {
      "category": "limits",
      "items": [
        { "key": "max_upload_size_mb", "value": "50", "updatedAt": "2026-01-01T00:00:00Z" }
      ]
    }
  ]
}
```

Values are always strings; callers parse/cast according to the known key (e.g. `max_upload_size_mb` as an integer, `registration_open` as a boolean).

## PATCH /api/admin/settings

### Request Body

```json
{ "settings": { "max_upload_size_mb": "100" } }
```

One or more key/value pairs. At least one must be supplied; every key must be a known setting.

### Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 200 | Applied | `Settings updated successfully` (returns the full updated settings, same shape as `GET`) |
| 400 | Empty `settings` map, or any key is not a known setting | `Validation failed` |
| 401 | JWT missing/invalid/expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |

Nothing is persisted for a batch if any key in it is unknown (the request fails fast on the first unrecognized key).

## Seeded settings

| Category | Key | Default | Meaning |
|----------|-----|---------|---------|
| auth | registration_open | `true` | Whether new-account registration is accepted. |
| content | auto_moderation_threshold | `0.5` | Content-filter relevance-score threshold (0.0–1.0). |
| ai | default_monthly_limit_override | *(empty)* | Optional override of the default plan's monthly request limit; empty means "use the plan's own limit." |
| limits | max_upload_size_mb | `50` | Maximum accepted upload size, in megabytes. |

## Business Rules

- Only an Administrator may view or change settings.
- Every successful change writes a `moderation_log` row: `action = UPDATE_SETTING`, `targetType = SETTING`, `targetId` (the setting's id), `reason` (`"<key>: '<old>' -> '<new>'"`) — who/what/when (BR-ADMIN-002's audit convention).
- Settings are read-model only in this change: wiring a value into live enforcement (e.g. actually rejecting registrations when `registration_open=false`) is a separate, per-setting follow-up, not part of this API.

## Non-goals

A settings UI (frontend) and environment-specific configs are out of scope (issue #13 non-goals).

## Related

- Shared error/response contract: `docs/apis/error-responses.md`
