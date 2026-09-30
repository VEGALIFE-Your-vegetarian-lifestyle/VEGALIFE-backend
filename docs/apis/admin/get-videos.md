# API Reference: GET /api/admin/videos

## Overview
Return a paginated list of every non-deleted video upload across all uploaders and all upload states, optionally filtered by upload status, uploader, and creation date range, with the video's technical metadata and its attached posts on each item. Admin only.

## Endpoint
```
GET /api/admin/videos
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`), role `ADMIN`.

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index, default 0 |
| size | integer | No | Page size, default 20, max 100 |
| sort | string | No | Format `property,direction`. Default `createdAt,desc`. Property must be one of: `createdAt`, `updatedAt`, `durationSeconds`, `fileSizeBytes` |
| status | string | No | One of: `uploading`, `succeed`, `failed` |
| userId | uuid | No | Only videos uploaded by this user |
| createdFrom | string | No | ISO-8601 datetime lower bound (inclusive) on `createdAt`, e.g. `2026-01-01T00:00:00Z` |
| createdTo | string | No | ISO-8601 datetime upper bound (inclusive) on `createdAt`, e.g. `2026-01-31T23:59:59Z` |

### Request Body
No request body

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Videos retrieved successfully",
  "data": {
    "content": [
      {
        "id": "3f1d2a4c-9b8e-4a7f-8c6d-1e2f3a4b5c6d",
        "mediaUrl": "https://cdn.example.com/media/vegan-stirfry.mp4",
        "thumbnailUrl": "https://cdn.example.com/media/vegan-stirfry-thumb.jpg",
        "description": "Weeknight tofu stir-fry",
        "status": "succeed",
        "durationSeconds": 87,
        "fileSizeBytes": 5242880,
        "mimeType": "video/mp4",
        "width": 1280,
        "height": 720,
        "externalId": "user-550e8400/3f1d2a4c",
        "createdAt": "2026-09-28T08:00:00Z",
        "updatedAt": "2026-09-28T08:02:11Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "email": "jane@example.com",
        "posts": [
          {
            "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "title": "Weeknight tofu stir-fry",
            "status": "published"
          }
        ]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | "Videos retrieved successfully" |
| data.content | array | Video items for this page |
| data.content[].id | uuid | Media id |
| data.content[].mediaUrl | string | Delivered file URL; null while `uploading` |
| data.content[].thumbnailUrl | string | Thumbnail URL, nullable |
| data.content[].description | string | Uploader-supplied description, nullable |
| data.content[].status | string | `uploading`, `succeed`, or `failed` |
| data.content[].durationSeconds | integer | Video duration in seconds, nullable until confirmed |
| data.content[].fileSizeBytes | integer | Actual byte count, nullable until confirmed |
| data.content[].mimeType | string | Video MIME type (`video/mp4` or `video/webm`) |
| data.content[].width | integer | Pixel width, nullable until confirmed |
| data.content[].height | integer | Pixel height, nullable until confirmed |
| data.content[].externalId | string | Provider object key, nullable until granted |
| data.content[].createdAt | string | ISO-8601 creation timestamp |
| data.content[].updatedAt | string | ISO-8601 last-update timestamp |
| data.content[].userId | uuid | Uploader user id, nullable for rows without an uploader |
| data.content[].username | string | Uploader username, nullable |
| data.content[].email | string | Uploader email, nullable |
| data.content[].posts | array | Non-deleted posts this video is attached to; empty when unattached |
| data.content[].posts[].id | uuid | Post id |
| data.content[].posts[].title | string | Post title |
| data.content[].posts[].status | string | Post moderation status: created, processed, published, unpublished, hidden, or flagged |
| data.page | integer | 0-based page index returned |
| data.size | integer | Page size used |
| data.totalElements | integer | Total matching videos |
| data.totalPages | integer | Total page count |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Unknown `sort` property, invalid `status`/date format, `createdFrom` after `createdTo`, or `size` > 100 | "Validation failed" or the parse error message |
| 401 | Missing/invalid JWT | "Unauthorized" |
| 403 | Authenticated non-admin | "Forbidden" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Only non-deleted rows are listed (`deletedAt != null` excluded) — issue #3 / FR-007.
- Only video MIME types are listed; `image/jpeg`, `image/png`, and `image/webp` rows never appear — issue #3 / FR-002.
- Only `ROLE_ADMIN` may call this endpoint (SecurityConfig `/api/admin/**`).
- Every upload state is listable, including `uploading` and `failed`, so admins can see abandoned and broken uploads — issue #3 / FR-003.
- The MIME-type allowlist and per-class size ceilings these files were accepted under are in `docs/brs/media.md`.

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/admin/videos?page=0&size=20&status=succeed&userId=550e8400-e29b-41d4-a716-446655440000&sort=createdAt,desc" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Videos retrieved successfully",
  "data": {
    "content": [
      {
        "id": "3f1d2a4c-9b8e-4a7f-8c6d-1e2f3a4b5c6d",
        "mediaUrl": "https://cdn.example.com/media/vegan-stirfry.mp4",
        "thumbnailUrl": "https://cdn.example.com/media/vegan-stirfry-thumb.jpg",
        "description": "Weeknight tofu stir-fry",
        "status": "succeed",
        "durationSeconds": 87,
        "fileSizeBytes": 5242880,
        "mimeType": "video/mp4",
        "width": 1280,
        "height": 720,
        "externalId": "user-550e8400/3f1d2a4c",
        "createdAt": "2026-09-28T08:00:00Z",
        "updatedAt": "2026-09-28T08:02:11Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "username": "jane",
        "email": "jane@example.com",
        "posts": [
          {
            "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "title": "Weeknight tofu stir-fry",
            "status": "published"
          }
        ]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

### Error Response (403)
```json
{
  "success": false,
  "message": "Forbidden",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/list-all-videos-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/3
- Business Rules: `docs/brs/media.md` (MIME allowlist, size ceilings, confirmation lifecycle), `docs/brs/auth.md` (JWT authentication)
