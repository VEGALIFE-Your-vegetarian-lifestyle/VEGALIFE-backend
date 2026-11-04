# API Reference: GET /api/subscriptions/me/history

## Overview
Return the authenticated member's full AI acquisition history — every plan they
have ever acquired, whether bought or claimed, newest first, paginated. This is
the endpoint that answers "which plan did this money buy"; the payment
endpoints are money-only and no longer carry a plan.

## Endpoint
```
GET /api/subscriptions/me/history
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`.

## Request

### Path Parameters
None.

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| page | integer | No | 0-based page index; default `0`, must be `>= 0`. An index past the last page returns 200 with empty `content` |
| size | integer | No | Page size; default `20`, must be between `1` and `100` |

### Request Body
No request body

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Acquisition history retrieved successfully",
  "data": {
    "content": [
      {
        "planCode": "PRO",
        "planName": "Pro",
        "source": "PURCHASE",
        "grantedAt": "2026-03-04T09:12:00Z",
        "paymentId": "3f2a9d21-5f6e-4c11-9a8b-1d2e3f4a5b6c",
        "amount": 49000,
        "currency": "VND",
        "status": "succeeded",
        "createdAt": "2026-03-04T09:11:41Z"
      },
      {
        "planCode": "FREE",
        "planName": "Free",
        "source": "CLAIM",
        "grantedAt": "2026-02-01T08:00:00Z",
        "paymentId": null,
        "amount": null,
        "currency": null,
        "status": null,
        "createdAt": "2026-02-01T08:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

Envelope fields follow `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`). Rows are ordered by `createdAt` descending — the member's most recent acquisition first.

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.content | array | One entry per acquisition, newest first |
| data.content[].planCode | string | Plan code (`FREE`, `PRO`, …) |
| data.content[].planName | string | Display name of the acquired plan |
| data.content[].source | string | `PURCHASE` when `paymentId` is set, `CLAIM` when it is `null` |
| data.content[].grantedAt | string \| null | ISO-8601 instant the grant became live. `null` for a checkout that has not been paid (pending or failed) — that row grants no quota |
| data.content[].paymentId | string (UUID) \| null | The `payment_ledger` row that paid for this acquisition; `null` for a claim |
| data.content[].amount | number \| null | Amount paid in whole VND; `null` for a claim |
| data.content[].currency | string \| null | Payment currency, currently always `VND` when present; `null` for a claim |
| data.content[].status | string \| null | Payment status: `pending`, `succeeded`, `failed` or `refunded`; `null` for a claim |
| data.content[].createdAt | string | ISO-8601 instant the acquisition was written (ordering key) |
| data.page / size | integer | Echo of the paging request (with defaults applied) |
| data.totalElements | integer | Total acquisitions for the member across all pages |
| data.totalPages | integer | Total pages for the requested `size` |
| data.first / last | boolean | Whether this is the first / last page |

Subscription ids, user ids, and internal acquisition ids are deliberately never returned.

A pending checkout appears with `grantedAt: null` and `status: "pending"`, so an abandoned payment is visible rather than silently absent.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `page < 0` or `size` outside 1–100 | "Validation failed" |
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 500 | Server error | "Internal server error" |

A member with no acquisitions gets 200 with empty `content`, `totalElements: 0`, `totalPages: 0` — never 404.

## Business Rules
- Read-only: no row is created or updated.
- BR-SUBS-007 — only rows with `granted_at IS NOT NULL` contribute to the allowance. A row with `grantedAt: null` is history but grants nothing.
- BR-SUBS-008 — `source` distinguishes an explicit free claim (`CLAIM`) from a purchase (`PURCHASE`); a claimed plan cannot be claimed twice.
- BR-PAY-010 — the acquisition carries the plan and the `paymentId`; amounts and payment status come from the linked `payment_ledger` row.
- BR-PAY-005 — a pending row inside the checkout TTL is reused by a later acquire call, so it stays visible here.
- Feature Spec FR-014: `docs/feats/request-credits.md`

## Example

### Request
```bash
curl -X GET "http://localhost:8080/api/subscriptions/me/history?page=0&size=10" \
  -H "Authorization: Bearer <access-token>"
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Acquisition history retrieved successfully",
  "data": {
    "content": [
      {
        "planCode": "PRO",
        "planName": "Pro",
        "source": "PURCHASE",
        "grantedAt": null,
        "paymentId": "8f14e45f-ceea-167a-5a36-dedd4bea2543",
        "amount": 49000,
        "currency": "VND",
        "status": "pending",
        "createdAt": "2026-03-04T09:11:41Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

## Related
- Feature Spec: `docs/feats/request-credits.md`
- ADR: `docs/adrs/021-request-allowance-as-acquisition-sum.md`
- Sibling endpoint: `docs/apis/subscriptions/get-me.md` (`GET /api/subscriptions/me`, remaining usage only)
- Sibling endpoint: `docs/apis/subscriptions/post-acquire.md` (`POST /api/subscriptions/acquire`, claim or purchase)
- Money-only counterpart: `docs/apis/payments/get-payments.md` (`GET /api/payments`, no plan context)
- Business Rules: `docs/brs/subscriptions.md`, `docs/brs/payments.md`
