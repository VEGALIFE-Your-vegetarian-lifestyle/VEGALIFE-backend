# API Reference: GET /api/media/{mediaId}

## Overview

Read back a stored media record, so a client can check whether an upload has been confirmed and get its delivery URL without re-running confirmation.

## Endpoint

```text
GET /api/media/{mediaId}
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`).

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| mediaId | uuid | Yes | Media ID. |

### Query Parameters

No query parameters.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Media retrieved successfully",
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

The payload has the same shape as a successful confirmation response. For a row still in `uploading`, `mediaUrl`, `fileSizeBytes`, `mimeType`, `width`, `height` and `durationSeconds` are all `null`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | No media row with that ID | `Media not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- [BR-MEDIA-002](../../brs/media.md) — Media Ownership Is Derived from Authentication

Media records are not currently access-controlled per owner: the product decision for this feature treats media as attachable across users, so this endpoint returns any existing row to any authenticated caller. See the "Risks / open questions" section of the feature spec.

## Example

```bash
curl http://localhost:8080/api/media/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Request a grant: `post-upload.md`
- Confirm the upload: `post-media-mediaid-confirm.md`
- Feature spec: `docs/feats/upload-media-api.md`
