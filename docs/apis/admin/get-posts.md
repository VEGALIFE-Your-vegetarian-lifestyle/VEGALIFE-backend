# API Reference: GET /api/admin/posts

## Overview
Return a paginated list of every non-deleted post across all users and all statuses, optionally filtered by status, author, category, and creation date range, with the semantic content-filter verdict on each item. Admin only.

## Endpoint
```
GET /api/admin/posts
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
| sort | string | No | Format `property,direction`. Default `createdAt,desc`. Property must be one of: `createdAt`, `publishedAt`, `updatedAt`, `viewCount`, `title` |
| status | string | No | One of: `drafted`, `processing`, `published`, `unpublished`. The review queue is `processing` |
| flag | string | No | Last verdict: `FILTER_PASSED`, `FILTER_FAILED`, `SIMILAR_CONTENT`, `FILTER_SKIPPED`, or `all`. Defaults to `all` when omitted, so nothing is silently hidden; matches exactly (ADR-025) |
| userId | uuid | No | Only posts authored by this user |
| categoryId | uuid | No | Only posts belonging to this category |
| createdFrom | string | No | ISO-8601 datetime lower bound (inclusive) on `createdAt`, e.g. `2026-01-01T00:00:00Z` |
| createdTo | string | No | ISO-8601 datetime upper bound (inclusive) on `createdAt`, e.g. `2026-01-31T23:59:59Z` |

### Request Body
No request body

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Posts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "title": "Vegan pho at home",
        "content": "A step-by-step guide to a clear mushroom broth...",
        "featuredImageUrl": "https://cdn.example.com/posts/pho.jpg",
        "categoryIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"],
        "mediaIds": [],
        "status": "published",
        "flag": null,
        "viewCount": 42,
        "publishedAt": "2026-09-28T08:15:00Z",
        "createdAt": "2026-09-28T08:00:00Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "displayName": "jane",
        "email": "jane@example.com"
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
| message | string | "Posts retrieved successfully" |
| data.content | array | Post items for this page |
| data.content[].id | uuid | Post id |
| data.content[].title | string | Post title |
| data.content[].content | string | Post body, kept for moderation review (issue #101) |
| data.content[].featuredImageUrl | string | Featured image URL, nullable |
| data.content[].categoryIds | uuid[] | Category ids the post belongs to |
| data.content[].mediaIds | uuid[] | Media ids attached to the post |
| data.content[].status | string | drafted, processing, published, or unpublished |
| data.content[].flag | string \| null | The post's last verdict: `FILTER_PASSED` (the filter cleared it), or `FILTER_FAILED` / `SIMILAR_CONTENT` / `FILTER_SKIPPED` (held, or approved over the blocker). `null` on a draft, on a run still in flight, and on rows published before V58 (ADR-025) |
| data.content[].flagDetail | object \| null | The evidence behind `flag` as a JSON snapshot (ADR-024, ADR-025): `kind` (always equal to `flag`) plus the fields that verdict owns — `score`/`threshold` for `FILTER_PASSED`, `outcome`/`score`/`threshold` for `FILTER_FAILED`, `postId`/`similarity`/`threshold` for `SIMILAR_CONTENT`, `cause` for `FILTER_SKIPPED`. Absent fields are omitted; null with no verdict |
| data.content[].rejectionReason | string | Why an admin rejected the post; null when no reason was recorded |
| data.content[].viewCount | integer | View counter |
| data.content[].publishedAt | string | ISO-8601 publish timestamp, nullable |
| data.content[].createdAt | string | ISO-8601 creation timestamp |
| data.content[].userId | uuid | Author user id |
| data.content[].displayName | string | Author's display name |
| data.content[].email | string | Author email |
| data.page | integer | 0-based page index returned |
| data.size | integer | Page size used |
| data.totalElements | integer | Total matching posts |
| data.totalPages | integer | Total page count |
| data.first | boolean | Whether this is the first page |
| data.last | boolean | Whether this is the last page |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Unknown `sort` property, invalid `status`/`flag`/date format, `createdFrom` after `createdTo`, or `size` > 100 | "Validation failed" or the parse error message |
| 401 | Missing/invalid JWT | "Unauthorized" |
| 403 | Authenticated non-admin | "Forbidden" |
| 500 | Server error | "Internal server error" |

## Business Rules
- Soft-deleted posts (`deletedAt != null`) are excluded (issue #1 / FR-006).
- Only `ROLE_ADMIN` may call this endpoint (`@PreAuthorize("hasRole('ADMIN')")` on `AdminPostController`; ADR-009 — there is no `/api/admin/**` path rule).
- Unlike `GET /api/posts/me` and `GET /api/users/{userId}/posts`, no status restriction is applied: every status is listable (issue #1 / FR-002).
- `status` and `flag` are independent filters and combine with AND; `flag` matches the verdict exactly (BR-FILTER-008).
- When no `flag` parameter is supplied the list defaults to `flag=all`, so no verdict is silently hidden (issue #5). The review queue is `status=processing`, which lists every post awaiting a decision regardless of verdict.
- `flag` is the post's last verdict and survives publication (ADR-025). At `processing` it says why the post is held (`FILTER_FAILED`, `SIMILAR_CONTENT`, `FILTER_SKIPPED`) or is `null` while the run is still in flight. At `published` it distinguishes a post the filter cleared (`FILTER_PASSED`) from one an admin approved over a blocker — the only place that distinction is readable.
- This is the only API surface that exposes `flag`/`flagDetail`; the member-facing post responses do not carry them (ADR-025).

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/admin/posts?page=0&size=20&status=processing&flag=SIMILAR_CONTENT&categoryId=1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed&sort=createdAt,desc" \
  -H "Authorization: Bearer <admin_access_token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Posts retrieved successfully",
  "data": {
    "content": [
      {
        "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "title": "Vegan pho at home",
        "content": "A step-by-step guide to a clear mushroom broth...",
        "featuredImageUrl": "https://cdn.example.com/posts/pho.jpg",
        "categoryIds": ["1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed"],
        "mediaIds": [],
        "status": "processing",
        "flag": "SIMILAR_CONTENT",
        "flagDetail": {
          "kind": "SIMILAR_CONTENT",
          "postId": "9a1f0d2c-3b4e-4c5d-8e6f-7a8b9c0d1e2f",
          "similarity": 0.9712,
          "threshold": 0.85
        },
        "viewCount": 42,
        "publishedAt": null,
        "createdAt": "2026-09-28T08:00:00Z",
        "userId": "550e8400-e29b-41d4-a716-446655440000",
        "displayName": "jane",
        "email": "jane@example.com"
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
- Feature Spec: `docs/feats/list-all-posts-admin-api.md`
- Driving issue: https://github.com/VEGALIFE-Your-vegetarian-lifestyle/VEGALIFE-backend/issues/1
- Business Rules: `docs/brs/posts.md` (post visibility), `docs/brs/auth.md` (JWT authentication)
