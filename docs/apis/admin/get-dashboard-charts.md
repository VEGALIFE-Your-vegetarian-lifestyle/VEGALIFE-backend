# API Reference: GET /api/admin/dashboard/charts

## Overview

Bucketed time series for the admin console. This is the **only** dashboard surface that carries a
time filter: two series — revenue by plan and published posts — sharing one bucket size so their
x-axes line up.

Revenue counts money that arrived; published posts counts content that reached readers. Nothing
else is plotted here. User acquisition, payment outcomes, and engagement are reported as
current-state totals on the lifetime summary instead, where they are not distorted by a short
window.

Every money amount is in whole dong (VND), and `meta.currency` says so. The platform settles in VND
only; the gateway notifies hundredths, but `payment_ledger.amount` — and therefore every amount here
— is already whole dong.

## Endpoint

```text
GET /api/admin/dashboard/charts
```

## Authentication

Required: a valid JWT access token in the `Authorization` header (`Bearer <token>`), role `ADMIN`.

## Request

### Query Parameters

| Name | Type | Required | Description |
|------|------|----------|-------------|
| range | string | No | One of `day`, `week`, `month`. Default `week`. Any other value → 400. |

`range` applies to **both** series: it is the single time filter for this endpoint.

`custom` is deliberately **not** supported. Arbitrary windows would need a maximum-span cap, a
maximum-bucket cap, and a caller-supplied bucket size that could desynchronise the series. With
three presets the server derives the bucket size and the window is bounded by construction.

### Range to bucket mapping

| `range` | `interval` | Buckets | Window |
|---------|-----------|---------|--------|
| `day` | `hour` | 24 | The current hour plus the 23 before it |
| `week` | `day` | 7 | Today plus the 6 days before it |
| `month` | `day` | 30 | Today plus the 29 days before it |

`month` is the last 30 days, not a calendar month, so its point count never varies with month
length. The window starts at the bucket boundary and ends at "now", so the **last bucket is partial**.

Buckets are **UTC-aligned**: a `day` bucket starts at 00:00 UTC, an `hour` bucket on the hour in UTC.
The alignment is fixed in the query rather than taken from the database session timezone, so the
series is identical whatever timezone the server runs in.

### Request Body

No request body.

## Responses

### Success Response (200 OK)

```json
{
  "success": true,
  "message": "Dashboard charts retrieved successfully",
  "data": {
    "meta": {
      "range": "week", "interval": "day",
      "from": "2026-11-14T00:00:00Z", "to": "2026-11-20T09:14:03Z",
      "boundary": "inclusiveStartExclusiveEnd",
      "currency": "VND",
      "generatedAt": "2026-11-20T09:14:03Z"
    },
    "revenueByPlan": {
      "items": [
        { "planId": "…", "code": "PRO", "name": "Pro", "amount": 2597000,
          "purchases": 53, "active": true },
        { "planId": "…", "code": "PLUS", "name": "Plus", "amount": 2079000,
          "purchases": 21, "active": true }
      ]
    },
    "publishedPosts": {
      "points": [
        { "bucketStart": "2026-11-14T00:00:00Z", "published": 12 },
        { "bucketStart": "2026-11-15T00:00:00Z", "published": 0 }
      ]
    }
  }
}
```

### Field Reference

| Field | Type | Description |
|-------|------|-------------|
| data.meta.range | string | The requested preset, echoed after validation. Applied to every series. |
| data.meta.interval | string | Bucket size derived by the server: `hour` or `day`. Never supplied by the caller. |
| data.meta.from | timestamp | Window start, inclusive. |
| data.meta.to | timestamp | Window end, exclusive. |
| data.meta.boundary | string | Always `inclusiveStartExclusiveEnd`, stated so a client labels the axis correctly. |
| data.meta.currency | string | Currency of every amount in `revenueByPlan`. Always `VND`: the platform settles in VND only. |
| data.meta.generatedAt | timestamp | When the payload was produced. The response is cached, so this is the freshness signal. |
| data.revenueByPlan.items[] | array | Succeeded revenue per plan **over the window**, ranked by `amount` descending. `amount` is what was actually charged, in whole dong (VND), read from the ledger — never the catalogue price multiplied by a count, or a price change would rewrite history. `purchases` is how many packages of that plan were bought in the same window. `active` is the plan's current state, kept so a deactivated plan that still earned in the window is visible. Draw as horizontal bars. |
| data.publishedPosts.points[] | array | One point per bucket. `published` is how many posts became visible to readers in that bucket. Counts only posts with status `published` that are not soft-deleted; drafts, queued, and rejected posts are excluded. Bucketed by `published_at`, so a post drafted weeks ago and released today lands in today's bucket. Draw as a bar or line. |

`amount` and `purchases` are independent measures: a plan with a high price can have fewer purchases
than a cheaper one, so derive neither from the other and do not divide one by the other to price a
package — that would blend an average across a window in which prices may have changed.

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `range` is not one of `day`, `week`, `month` | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may view the dashboard.
- The two series always share one bucket size, so a client can draw them against a single time
  scale. The bucket size is server-derived from `range` and is never a request parameter.
- The same `range` filters both series; there is no way to ask for revenue over one window and
  posts over another.
- The window is half-open, `[from, to)`, so a row written exactly on a boundary is counted once.
  Because the window ends at "now", the last bucket is partial.
- A bucket with no rows is returned as zero rather than omitted, so the series has no gaps.
- Buckets are UTC-aligned regardless of the server's timezone, so a `day` bucket is not a local
  calendar day in every zone.
- `publishedPosts` measures what readers saw, not what authors submitted. Content still in the
  moderation queue appears on the summary as pending, never here.
- Revenue counts only `succeeded` payments. There is no refund write path in this codebase, so the
  figures are gross, not net.
- Results are cached in-process (Caffeine) for 15 minutes, keyed by `range`. The lifetime summary is
  cached separately with a shorter TTL. Both are cleared when a write changes what they report —
  a settled payment, an approve/reject, or a plan activation toggle — so the TTL bounds staleness
  rather than defining it. See `docs/feats/admin-dashboard-cache-invalidation.md`.

## Non-goals

A caller-supplied `interval`, an arbitrary `custom` window, and per-bucket CSV/PDF export are out of
scope. Real-time streaming metrics are also out of scope.

## Related

- Lifetime snapshot: `docs/apis/admin/get-dashboard.md`
- Payments: `docs/apis/admin/get-payments.md`
- Shared error/response contract: `docs/apis/error-responses.md`
