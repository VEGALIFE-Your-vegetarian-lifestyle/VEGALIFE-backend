# API Reference: GET /api/admin/dashboard

## Overview

Lifetime and current-state platform metrics for the admin console: account counts, content totals,
engagement totals, the moderation backlog, and lifetime money figures. This endpoint carries **no
time filter** — every value is either a lifetime total or the state of the platform right now.
Anything that moves over time is served by `GET /api/admin/dashboard/charts`.

## Endpoint

```text
GET /api/admin/dashboard
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Query Parameters

None. Any query parameter sent is ignored.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Dashboard retrieved successfully",
  "data": {
    "meta": { "generatedAt": "2026-11-20T09:14:03Z", "currency": "VND", "currencyExponent": 0 },
    "users": {
      "total": 1024, "emailVerified": 870, "activatedEver": 410,
      "suspended": 6, "deactivated": 31, "activationRate": 0.8496
    },
    "content": { "totalPosts": 512, "totalRecipes": 210, "totalComments": 3300 },
    "engagement": {
      "totalVotes": 4820, "votesUp": 4410, "votesDown": 410,
      "totalSaves": 640, "savedPosts": 480, "savedRecipes": 160
    },
    "moderationQueue": {
      "pendingPosts": 4, "pendingRecipes": 2, "reportedComments": 6,
      "oldestPendingAgeHours": 42, "filterSkippedPosts": 1
    },
    "money": {
      "revenueTotal": 4900000, "transactionsTotal": 100, "payingUsersTotal": 74,
      "averageOrderValue": 49000.0, "arpu": 66216.22,
      "freeGrantsTotal": 930, "payerConversionRate": 0.0723, "pendingStaleCount": 3
    }
  }
}
```

### Field Reference

| Field | Type | Description |
|-------|------|-------------|
| data.meta.generatedAt | timestamp | When the payload was produced. The response is cached, so this — not any window boundary — is the only freshness claim a client may render. |
| data.meta.currency | string \| null | Currency of `money.revenueTotal`, or `null` when the ledger holds no succeeded payment or holds more than one. A total summed across currencies is meaningless, so the mixed case is signalled rather than hidden. |
| data.meta.currencyExponent | integer | Decimal exponent of `currency` (0 for VND). `payment_ledger.amount` is stored in whole currency units while the gateway notifies hundredths. |
| data.users.total | integer | Non-deleted registered accounts. |
| data.users.emailVerified | integer | Accounts with `email_verified = true`. Set once, so it never reverts. |
| data.users.activatedEver | integer | Accounts that have ever consumed an AI request (`ai_usage.request_count > 0`). The only monotonic activation signal — `user.status = activated` is current state and is overwritten by a suspend or deactivate. |
| data.users.suspended / deactivated | integer | Accounts currently in that `user.status`. |
| data.users.activationRate | number | `emailVerified / total`, as a fraction. `0` when there are no accounts. |
| data.content.totalPosts / totalRecipes / totalComments | integer | Lifetime non-deleted rows of each type. |
| data.engagement.totalVotes | integer | Lifetime votes cast. |
| data.engagement.votesUp / votesDown | integer | The split by `vote.vote_type`, so the total carries sentiment rather than only volume. |
| data.engagement.totalSaves | integer | `savedPosts + savedRecipes`. |
| data.engagement.savedPosts / savedRecipes | integer | Lifetime saves of each target. A row saves exactly one of the two. |
| data.moderationQueue.pendingPosts | integer | Every non-deleted post with `status = pending` — the same predicate as `GET /api/admin/posts?status=pending` (ADR-029). |
| data.moderationQueue.pendingRecipes | integer | Recipes with `moderationStatus = PENDING`. |
| data.moderationQueue.reportedComments | integer | Non-deleted comments with `reportCount > 0`. |
| data.moderationQueue.oldestPendingAgeHours | integer | Age in whole hours of the single oldest item across all three queues; `0` when nothing is waiting. More actionable than the counts: three items waiting six days is worse than thirty waiting an hour. |
| data.moderationQueue.filterSkippedPosts | integer | Posts with `flag = FILTER_SKIPPED` — the content filter never produced a verdict. |
| data.money.revenueTotal | integer | Sum of `payment_ledger.amount` where `status = succeeded`. Whole currency units. Gross, not net: there is no refund write path in this codebase, so `refunded` rows never exist. |
| data.money.transactionsTotal | integer | Count of succeeded payments. |
| data.money.payingUsersTotal | integer | Distinct members with at least one succeeded payment. |
| data.money.averageOrderValue | number | `revenueTotal / transactionsTotal`. `0` when nothing succeeded. |
| data.money.arpu | number | `revenueTotal / payingUsersTotal`. `0` when nobody has paid. |
| data.money.freeGrantsTotal | integer | Acquisitions granted for free (`ai_subscription.payment_id IS NULL`) — allowance handed out with no money behind it. |
| data.money.payerConversionRate | number | `payingUsersTotal / users.total`, as a fraction. |
| data.money.pendingStaleCount | integer | Payments still `pending` more than 24 hours after checkout. Nothing expires or reconciles them, so this pool only grows. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may view the dashboard.
- No value on this endpoint is range-limited. Lifetime totals never reset; queue values are the
  current snapshot. Use the charts endpoint for anything time-based.
- Results are cached in-process (Caffeine) for 5 minutes under a single unkeyed entry, so repeated
  loads within the window skip the underlying count queries.
- `revenueTotal` counts only `succeeded` rows. It is deliberately not described as net revenue,
  because no refund path writes a `refunded` status.
- `activatedEver` uses `ai_usage`, not `user.status`: a suspension overwrites the latter, so it can
  shrink and is not an "ever activated" figure.

## Non-goals

Real-time streaming metrics, a custom dashboard builder, and CSV/PDF export remain out of scope.
Time-series and per-bucket data are deliberately excluded from this endpoint and live on
`GET /api/admin/dashboard/charts`.

## Related

- Time series: `docs/apis/admin/get-dashboard-charts.md`
- Content moderation: `docs/apis/admin/post-posts-postid-approve.md`, `docs/apis/admin/recipe-crud.md`, `docs/apis/admin/post-comments-commentid-moderate.md`
- Payments: `docs/apis/admin/get-payments.md`
- Shared error/response contract: `docs/apis/error-responses.md`
