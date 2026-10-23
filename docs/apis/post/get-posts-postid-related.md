# API Reference: GET /api/posts/{postId}/related

## Overview

Semantically similar posts, by title+content embedding cosine similarity (issue #7). Reuses the `post_embedding` vectors the content filter already computes at publish time (ADR-015) — no new embedding call is made for this endpoint.

## Endpoint

```text
GET /api/posts/{postId}/related
```

## Authentication

None required — public, like `GET /api/posts/{postId}` and `GET /api/posts/feed`.

## Request

### Path Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| postId | UUID | Yes | The source post. Must exist and not be soft-deleted; any status is accepted as a source. |

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| limit | integer | No | Number of results, 1–50. Default `10`. Outside range → 400. |

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Related posts retrieved successfully",
  "data": [
    {
      "post": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "title": "Rau củ hấp",
        "content": "Món chay đơn giản cho bữa tối.",
        "status": "published",
        "...": "same shape as GET /api/posts/{postId}"
      },
      "similarityScore": 0.93
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| data[].post | object | Same shape as `PostListResponse` (`GET /api/posts/{postId}`). |
| data[].similarityScore | decimal | Cosine similarity to the source post's embedding, in `[-1, 1]`; higher means more similar. |

Results are ranked by `similarityScore` descending. An empty array (`200 OK`) is returned when the source post has no stored embedding yet (never filtered, or embedding disabled) or no published match exists — never a `404` for "no matches."

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `limit` outside 1–50 | `Validation failed` |
| 404 | `postId` does not exist or is soft-deleted | `Post not found` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only `published`, non-deleted posts are eligible as matches — a draft, unpublished, or rejected post never appears in another post's related list (mirrors the duplicate-detection search in `DuplicateDetector`, ADR-015).
- The source post itself is always excluded from its own results.
- A match is only considered when its embedding was produced by the same model as the source's (`post_embedding.model`); rows from a retired provider are never compared against current ones (ADR-014, ADR-015).
- Similarity ranking is global (not personalized) and posts-only — recipes and videos are out of scope (issue #7 non-goals).

## Example

```bash
curl -X GET "http://localhost:8080/api/posts/550e8400-e29b-41d4-a716-446655440000/related?limit=5"
```

## Related

- Post detail: `docs/apis/post/get-posts-postid.md`
- Duplicate detection (same embedding infrastructure): `docs/adrs/015-post-embedding-pgvector.md`
- Shared error/response contract: `docs/apis/error-responses.md`
