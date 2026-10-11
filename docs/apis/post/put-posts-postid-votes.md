# API Reference: PUT /api/posts/{postId}/votes

## Overview

Cast, switch, or remove the authenticated user's vote on a post. One vote per user per post (`BR-VOTE-001`).

## Endpoint

```text
PUT /api/posts/{postId}/votes
```

## Authentication

Required: a valid JWT access token in `Authorization: Bearer <token>`. The user ID is taken from the authenticated principal.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | ID of the published post to vote on. |

### Request Body

```json
{
  "voteType": "UPVOTE"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| voteType | `"UPVOTE"` or `"DOWNVOTE"` | Yes | The vote to cast. Casting the same type the caller already has removes it; casting the other type switches it. |

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Vote recorded successfully",
  "data": {
    "postId": "85fb5914-f304-45c9-b44e-480e90ec67fa",
    "likeCount": 12,
    "dislikeCount": 1,
    "userVote": "UPVOTE"
  }
}
```

`userVote` is `null` when the call just removed the caller's vote (same type cast twice in a row).

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `voteType` missing or not `UPVOTE`/`DOWNVOTE` | `Validation failed` / `Malformed request body` |
| 401 | JWT is missing, invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | Post is missing, deleted, or not published | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- One vote per user per post, toggled between like, dislike, and removed — `BR-VOTE-001`.
- Only published, non-deleted posts can be voted on.
- Voting on comments is not supported (issue #45 non-goal).

## Related

- Feature spec: `docs/feats/vote-api.md`
- Read votes: `docs/apis/post/get-posts-postid-votes.md`
