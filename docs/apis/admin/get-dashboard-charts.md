# API Reference: GET /api/admin/dashboard/charts

## Overview

Bucketed time series for the admin console. This is the **only** dashboard surface that carries a
time filter: four series — revenue trend, revenue by plan, payment health, and growth — all sharing
one bucket size so their x-axes line up.

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

`custom` is deliberately **not** supported. Arbitrary windows would need a maximum-span cap, a
maximum-bucket cap, and a caller-supplied bucket size that could desynchronise the four series.
With three presets the server derives the bucket size and the window is bounded by construction.

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
    "revenueTrend": {
      "points": [
        { "bucketStart": "2026-11-14T00:00:00Z", "amount": 245000, "transactions": 5 }
      ]
    },
    "revenueByPlan": {
      "items": [
        { "planId": "…", "code": "PRO", "name": "Pro", "amount": 4900000,
          "transactions": 100, "active": true }
      ]
    },
    "paymentHealth": {
      "points": [
        { "bucketStart": "2026-11-14T00:00:00Z", "succeeded": 12, "failed": 3,
          "pendingFresh": 1, "pendingStale": 2 }
      ],
      "totals": { "succeeded": 40, "failed": 8, "pendingFresh": 2, "pendingStale": 6,
                  "successRate": 0.8333 }
    },
    "growth": {
      "points": [
        { "bucketStart": "2026-11-14T00:00:00Z", "newUsers": 40, "newPayingUsers": 3 }
      ]
    }
  }
}
```

### Field Reference

| Field | Type | Description |
|-------|------|-------------|
| data.meta.range | string | The requested preset, echoed after validation. |
| data.meta.interval | string | Bucket size derived by the server: `hour` or `day`. Never supplied by the caller. |
| data.meta.from | timestamp | Window start, inclusive. |
| data.meta.to | timestamp | Window end, exclusive. |
| data.meta.boundary | string | Always `inclusiveStartExclusiveEnd`, stated so a client labels the axis correctly. |
| data.meta.currency | string | Currency of every amount in `revenueTrend` and `revenueByPlan`. Always `VND`: the platform settles in VND only. |
| data.meta.generatedAt | timestamp | When the payload was produced. The response is cached, so this is the freshness signal. |
| data.revenueTrend.points[] | array | One point per bucket. `amount` is succeeded revenue in whole dong (VND); `transactions` is how many payments produced it, so a hover can tell one large payment from several ordinary ones. Draw as a line or area. |
| data.revenueByPlan.items[] | array | Succeeded revenue per plan, ranked by `amount` descending. `amount` is what was actually charged, in whole dong (VND), read from the ledger — never the catalogue price multiplied by a count, or a price change would rewrite history. `active` is the plan's current state, kept so a deactivated plan that still earned in the window is visible. Draw as horizontal bars. |
| data.paymentHealth.points[] | array | One point per bucket. `succeeded` is bucketed by `paid_at`; `failed` by `updated_at` (the ledger has no `failed_at`, so this is a documented proxy); pending by `created_at`. `pendingFresh` / `pendingStale` split at 24 hours of age. Draw as a stacked bar — the four values partition the attempts that started in the bucket. |
| data.paymentHealth.totals | object | Window totals computed from the rows themselves, plus `successRate = succeeded / (succeeded + failed)`. `pending` is excluded from the denominator: a healthy day with payments still in flight would otherwise look bad. |
| data.growth.points[] | array | New non-deleted accounts per bucket and distinct members whose **first** succeeded payment landed in that bucket, both counting people, so one axis serves both. No ratio is derived: the two counts cover different populations (a member who registered months ago and pays today is a first-time payer today but not a new user today), so dividing them would not be a conversion rate and could exceed 1. For conversion, use `money.payerConversionRate` on the summary. |

### Error Responses

| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `range` is not one of `day`, `week`, `month` | `Validation failed` |
| 401 | JWT is missing, invalid, or expired | `Unauthorized` |
| 403 | Authenticated but not an Administrator | `Forbidden` |
| 500 | Unexpected server error | `Internal server error` |

## Business Rules

- Only an Administrator may view the dashboard.
- The four series always share one bucket size, so a client can draw them against a single time
  scale. The bucket size is server-derived from `range` and is never a request parameter.
- The window is half-open, `[from, to)`, so a row written exactly on a boundary is counted once.
  Because the window ends at "now", the last bucket is partial.
- A bucket with no rows is returned as zero rather than omitted, so the series has no gaps.
- Buckets are UTC-aligned regardless of the server's timezone, so a `day` bucket is not a local
  calendar day in every zone.
- `newPayingUsers` counts a member only in the bucket where their first succeeded payment landed, so
  a repeat buyer is not counted again. No conversion ratio is derived per bucket: the two counts
  cover different populations, so the trustworthy conversion figure is `money.payerConversionRate`
  on the summary.
- Revenue counts only `succeeded` payments. There is no refund write path in this codebase, so the
  figures are gross, not net.
- Results are cached in-process (Caffeine) for 15 minutes, keyed by `range`. The lifetime summary is
  cached separately with a shorter TTL.
- `pendingStale` exists because nothing in this codebase expires or reconciles a pending payment: a
  checkout whose notification never arrives stays pending forever, so that pool only grows.

## Non-goals

A caller-supplied `interval`, an arbitrary `custom` window, and per-bucket CSV/PDF export are out of
scope. Real-time streaming metrics are also out of scope.

## Related

- Lifetime snapshot: `docs/apis/admin/get-dashboard.md`
- Payments: `docs/apis/admin/get-payments.md`
- Shared error/response contract: `docs/apis/error-responses.md`
