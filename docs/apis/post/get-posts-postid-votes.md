# API Reference: GET /api/posts/{postId}/votes

## Overview

Read a post's vote counts. Public — no authentication required; if the caller is identified by a valid JWT, their own current vote is also returned (`BR-VOTE-002`).

## Endpoint

```text
GET /api/posts/{postId}/votes
```

## Authentication

None required. If `Authorization: Bearer <token>` is present and valid, the response's `userVote` reflects that caller's vote; otherwise `userVote` is `null`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the published post whose votes to read. |

No query parameters or request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Votes retrieved successfully",
  "data": {
    "postId": "85fb5914-f304-45c9-b44e-480e90ec67fa",
    "likeCount": 12,
    "dislikeCount": 1,
    "userVote": null
  }
}
```

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | A JWT *was* sent but is invalid/expired (the route itself needs no token, but a bad one sent anyway is still rejected by the JWT filter, same as every other endpoint) | `Unauthorized` |
| 404 | Post is missing, deleted, or not published | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Reading vote counts requires no authentication; the caller's own vote is shown only when they are identified — `BR-VOTE-002`.
- Only published, non-deleted posts expose their votes (same visibility rule as the `PUT` endpoint).

## Related

- Feature spec: `docs/feats/vote-api.md`
- Cast a vote: `docs/apis/post/put-posts-postid-votes.md`
