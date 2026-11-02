# API Reference: GET /api/admin/dashboard

## Overview

Aggregated platform-health metrics for the admin console (issue #12): user counts, content counts, engagement, and the current moderation queue size, with time-range filtering.

## Endpoint

```text
GET /api/admin/dashboard
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|--------------|
| range | string | No | One of `day`, `week`, `month`, `custom`. Default `week`. Any other value → 400. |
| from | date (`yyyy-MM-dd`) | When `range=custom` | Inclusive start date. |
| to | date (`yyyy-MM-dd`) | When `range=custom` | Inclusive end date. Must not be before `from`. |

`day`/`week`/`month` are rolling windows ending now (e.g. `week` = the last 7 days up to the current moment), not calendar-aligned.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Dashboard retrieved successfully",
  "data": {
    "range": { "type": "week", "from": "2026-10-15T00:00:00Z", "to": "2026-10-22T09:00:00Z" },
    "users": { "total": 1024, "newInRange": 37, "activeInRange": 142 },
    "content": {
      "totalPosts": 512, "newPosts": 28,
      "totalRecipes": 210, "newRecipes": 9,
      "totalComments": 3300, "newComments": 150
    },
    "engagement": { "votesInRange": 420, "commentsInRange": 150 },
    "moderationQueue": { "pendingPosts": 4, "pendingRecipes": 2, "reportedComments": 6 }
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| data.range.type | string | The resolved range: `day`, `week`, `month`, or `custom`. |
| data.range.from, data.range.to | timestamp | The resolved window boundaries (UTC), inclusive of `from`, exclusive of `to`. |
| data.users.total | integer | Total registered accounts (non-deleted). |
| data.users.newInRange | integer | Accounts created within the range. |
| data.users.activeInRange | integer | Distinct users who authored a post or comment within the range. |
| data.content.totalPosts/newPosts, totalRecipes/newRecipes, totalComments/newComments | integer | Lifetime totals and in-range creates for each content type (non-deleted). |
| data.engagement.votesInRange | integer | Votes cast within the range. |
| data.engagement.commentsInRange | integer | Same as `content.newComments`, surfaced under engagement too. |
| data.moderationQueue.pendingPosts | integer | Posts flagged `NEEDS_REVIEW`, not yet moderated (current snapshot, not range-limited). |
| data.moderationQueue.pendingRecipes | integer | Recipes with `moderationStatus = PENDING` (current snapshot). |
| data.moderationQueue.reportedComments | integer | Comments with `reportCount > 0`, not deleted (current snapshot). |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Invalid `range`, or `range=custom` without both `from`/`to`, or `from` after `to` | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may view the dashboard.
- Results are cached in-process (Caffeine) for 5 minutes, keyed by the resolved range/from/to, so repeated loads within the window skip the underlying count queries.
- Moderation-queue counts are always the current snapshot — they are never limited by the selected time range, since they represent "what needs attention right now."

## Non-goals

Real-time streaming metrics, a custom dashboard builder, and CSV/PDF export are out of scope (issue #12 non-goals).

## Related

- Content moderation: `docs/apis/admin/post-posts-postid-approve.md`, `docs/apis/admin/recipe-crud.md`, `docs/apis/admin/post-comments-commentid-moderate.md`
- Shared error/response contract: `docs/apis/error-responses.md`
