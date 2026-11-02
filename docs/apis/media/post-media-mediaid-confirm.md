# API Reference: POST /api/media/{mediaId}/confirm

## Overview

Tell the backend the file has been uploaded. The backend reads the object back from the provider — never from anything the client claims — and, if it is present and within limits, promotes the media row to `succeed` and returns its delivery URL.

## Endpoint

```text
POST /api/media/{mediaId}/confirm
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`).

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| mediaId | uuid | Yes | Media ID returned by `POST /api/media/upload`. |

### Query Parameters

No query parameters.

### Request Body

No request body. Send `Content-Type: application/json` with an empty object, or omit the body entirely.

The client must not send `mediaUrl`, `fileSizeBytes`, `mimeType`, `width`, `height`, `durationSeconds` or a provider object key — every one of these is read back from the provider during this call.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Media upload confirmed",
  "data": {
    "mediaId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "succeed",
    "mediaUrl": "https://res.cloudinary.com/vegalife/image/upload/v1759245600/vegalife/550e8400-.../550e8400-....jpg",
    "thumbnailUrl": null,
    "description": null,
    "mimeType": "image/jpeg",
    "fileSizeBytes": 348966,
    "width": 1600,
    "height": 1200,
    "durationSeconds": null,
    "createdAt": "2026-09-30T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | `true` when the request succeeds. |
| message | string | `Media upload confirmed`. |
| data.mediaId | uuid | The confirmed media ID. |
| data.status | string | `succeed`. This is the value `PostService.resolveMedia()` requires before a post may reference it. |
| data.mediaUrl | string | Provider delivery URL for the uploaded object. |
| data.thumbnailUrl | string or null | `null` — thumbnail generation is a non-goal of this feature. |
| data.description | string or null | `null`; reserved for future alt-text / caption support. |
| data.mimeType | string | MIME type reported by the provider for the actual object. |
| data.fileSizeBytes | number | **Actual** size reported by the provider, not the client's declared `sizeBytes`. |
| data.width / data.height | integer or null | Pixel dimensions reported by the provider; `null` for video unless reported. |
| data.durationSeconds | integer or null | Video duration; `null` for images. |
| data.createdAt | string | ISO-8601 timestamp of the original grant, not of this confirmation. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | The provider has no object under the stored key (nothing was uploaded, or it was deleted) | `Upload verification failed` |
| 400 | The provider reports a real size above the ceiling for this row's media class | `File exceeds the 5 MB image limit` / `File exceeds the 50 MB limit` |
| 400 | The grant TTL elapsed before confirmation | `Upload grant has expired` |
| 400 | The media row's status is `failed` — it can never be confirmed | `Upload verification failed` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | No media row with that ID | `Media not found` |
| 409 | The row is already `succeed` — confirmation is exactly once | `Media upload has already been confirmed` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- [BR-MEDIA-002](../../brs/media.md) — Media Ownership Is Derived from Authentication
- [BR-MEDIA-003](../../brs/media.md) — Per-Class Upload Size Ceilings
- [BR-MEDIA-005](../../brs/media.md) — Upload Grants Expire
- [BR-MEDIA-007](../../brs/media.md) — Confirmation Is Exactly Once
- [BR-MEDIA-008](../../brs/media.md) — Confirmation Trusts Only Provider Read-Back

## Example

```bash
curl -X POST http://localhost:8080/api/media/550e8400-e29b-41d4-a716-446655440000/confirm \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{}'
```

## Related

- Request a grant: `post-upload.md`
- Read a media record: `get-media-mediaid.md`
- Attach to a post: `../post/post-posts.md`
- Feature spec: `docs/feats/upload-media-api.md`
