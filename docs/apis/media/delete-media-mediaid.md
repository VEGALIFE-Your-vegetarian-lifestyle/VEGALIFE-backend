# API Reference: DELETE /api/media/{mediaId}

## Overview

Delete a media record: soft-deletes the row (`deleted_at`) so it disappears from every read path, and enqueues an asynchronous purge that later destroys the stored provider object. The response never waits on the provider.

## Endpoint

```text
DELETE /api/media/{mediaId}
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
  "message": "Media deleted successfully",
  "data": null
}
```

Called again on an already-soft-deleted row, the response is identical: the delete is idempotent and no second purge message is enqueued.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 403 | The caller is neither the media's `uploaded_by` nor an administrator | `Forbidden` |
| 404 | No media row with that ID | `Media not found` |
| 500 | Unexpected server error | `Internal server error` |

A media row that exists but is already soft-deleted answers `200` (idempotent), not `404` — `404` means the row never existed. `GET /api/media/{mediaId}` returns `404` for a soft-deleted row, so a client that read first and then deleted sees `404` only on the GET, never on a repeat DELETE.

## Business Rules

- [BR-MEDIA-009](../../brs/media.md) — Media Deletion Is Owner-or-Admin, Idempotent, and Asynchronous
- [BR-MEDIA-010](../../brs/media.md) — Deleted Media Are Invisible on Every Read Path
- [BR-MEDIA-002](../../brs/media.md) — Media Ownership Is Derived from Authentication

Unlike the read endpoints (which stay open to any authenticated caller per BR-MEDIA-002), delete enforces ownership: the row's `uploaded_by` must match the JWT principal, or the caller must hold `ROLE_ADMIN`.

## Example

```bash
curl -X DELETE http://localhost:8080/api/media/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <access-token>"
```

## Related

- Request a grant: `post-upload.md`
- Confirm the upload: `post-media-mediaid-confirm.md`
- Read back a record: `get-media-mediaid.md`
- Feature spec: `docs/feats/delete-media-api.md`
- Delivery mechanism: `docs/feats/outbound-message-queue.md` (ADR-005)
