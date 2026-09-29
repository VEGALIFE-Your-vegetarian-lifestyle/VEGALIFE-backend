# API Reference: GET /api/users/{userId}/posts

## Overview

Return a paginated list of a member's posts. Public: guests can read a member's published posts (BR-PUBLIC-001, BR-SEARCH-002).

## Endpoint

```text
GET /api/users/{userId}/posts
```

## Authentication

Optional. No JWT is needed, but if a valid token is sent it decides how much the caller sees. An invalid or expired token still returns `401`.

| Caller | Posts returned |
|--------|----------------|
| Guest or any other member | Only posts with status `published` |
| The post owner | All non-deleted posts, any status |
| Administrator | All non-deleted posts, any status |

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| userId | UUID | Yes | ID of the member whose posts are listed. |

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`; must be `>= 0`. |
| size | integer | No | Page size. Defaults to `20`; between `1` and `100`. |

Public listings are ordered by `publishedAt` descending; owner/admin listings by `createdAt` descending. Order cannot be changed.

## Responses

### Success Response (200 OK)

Same envelope and item shape as `GET /api/posts` (`PageResponse<PostListResponse>`), message `Posts retrieved successfully`.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page` or `size` out of range | `Validation failed` |
| 401 | A token is supplied but is invalid, expired, or the account is inactive | `Unauthorized` |
| 404 | The user does not exist or is deleted | `User not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Drafts, hidden, unpublished, and processed posts are visible only to the creator and Administrators (BR-CONTENT-003).
- Soft-deleted posts are never returned.
- No migration is needed.

## Example

```bash
curl "http://localhost:8080/api/users/550e8400-e29b-41d4-a716-446655440000/posts?page=0&size=10"
```

## Related

- Feature spec: `docs/feats/list-posts-of-user.md`
- Business rules: `docs/brs/posts.md`
- List your own posts: `docs/apis/post/get-posts.md`
