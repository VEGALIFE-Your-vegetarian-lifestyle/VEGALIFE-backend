# API Reference: GET /api/posts

## Overview

Return a paginated, platform-wide feed of published posts, newest first. Public: guests can read it (BR-PUBLIC-001, BR-SEARCH-002, BR-POST-010).

## Endpoint

```text
GET /api/posts
```

## Authentication

Optional. No JWT is needed; the route is `permitAll`. If a token is sent it is still validated — an invalid or expired token returns `401`.

Every caller gets the same result: published, non-deleted posts only.

## Request

### Path Parameters

No path parameters.

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | Zero-based page index. Defaults to `0`; must be `>= 0`. |
| size | integer | No | Page size. Defaults to `20`; between `1` and `100`. |
| keyword | string | No | Case-insensitive whole-phrase match on `title` or the plain-text `content`. Trimmed; blank means no filter. `%`, `_`, and `\` are matched literally. |
| categoryId | UUID | No | Only posts assigned that category. A post is never returned twice. |
| createdFrom | instant | No | Inclusive lower bound on `createdAt`, ISO-8601. |
| createdTo | instant | No | Inclusive upper bound on `createdAt`, ISO-8601. |

Order is fixed: `published_at DESC, created_at DESC`. It cannot be changed. Filters combine with AND and only narrow the published feed — no filter can make a non-published or deleted post appear.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

Standard pagination envelope (`ApiResponse<PageResponse<PostListResponse>>`), message `Posts retrieved successfully`:

```json
{
  "success": true,
  "message": "Posts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "userId": "9f1c2f4a-8a3b-4c5d-9e6f-7a8b9c0d1e2f",
        "displayName": "John Doe",
        "avatarUrl": "https://res.cloudinary.com/vegalife/avatar/john.jpg",
        "title": "Vegan pho for beginners",
        "status": "published",
        "publishedAt": "2026-10-01T09:15:00",
        "createdAt": "2026-10-01T09:10:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

Item fields follow the existing `PostListResponse` shape used by `GET /api/posts/me` — including the author summary (`userId`, `displayName`, `avatarUrl`). See that endpoint's field table for the full set.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page`/`size` out of range, or an unparseable `categoryId`/instant | `Validation failed` |
| 401 | A token is supplied but is invalid, expired, or the account is inactive | `Unauthorized` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- BR-POST-010: only `published`, non-deleted posts are public; `drafted`, `processing` (the post is awaiting a filter verdict or an admin decision), and `unpublished` posts never appear (BR-CONTENT-003 keeps them private to owner and Administrators). The content filter never unpublishes a post; it holds it at `processing` with a blocker for an admin (BR-FILTER-008).
- BR-POST-003 pagination bounds apply: page `0`, size `20`, `size` 1–100.
- `keyword` matches the title or the plain-text `content` only — not `rawContent`, the author, or attached recipes. Matching is a whole trimmed phrase, case-insensitive, with no tokenization or ranking (BR-POST-014).
- `categoryId`, `createdFrom`, and `createdTo` only narrow the published feed; they never widen visibility.
- Soft-deleted posts (`deleted_at IS NOT NULL`) are never returned.
- `rejectionReason` is never populated on the feed: it is owner-scoped and this endpoint has no owner. A published post carrying a stale reason still returns `null`.
- No schema migration is needed.

## Example

```bash
curl "http://localhost:8080/api/posts?page=0&size=10&keyword=pho&categoryId=7c9e6679-7425-40de-944b-e07fc1f90ae7"
```

## Related

- Feature spec: `docs/feats/posts-feed.md`
- Business rules: `docs/brs/posts.md`
- List your own posts: `docs/apis/post/get-posts-me.md`
- List a member's posts: `docs/apis/post/get-users-userid-posts.md`
- Shared error/response contract: `docs/apis/error-responses.md`
