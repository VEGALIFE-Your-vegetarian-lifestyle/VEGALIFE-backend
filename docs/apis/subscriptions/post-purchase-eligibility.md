# API Reference: POST /api/subscriptions/me/purchase/eligibility

## Overview
Check — without writing anything — whether purchasing a given plan is currently allowed for the authenticated member: new purchase, same-plan extension, or blocked (409).

## Endpoint
```
POST /api/subscriptions/me/purchase/eligibility
```

## Authentication
JWT Bearer token required. A missing, expired, or invalid token returns `401 Unauthorized`.

## Request

### Path Parameters
None.

### Query Parameters
None.

### Request Body
```json
{
  "planCode": "string — plan code to check, e.g. \"PRO\"; required, non-blank"
}
```

The plan is identified by `planCode` (the same key checkout uses), because `GET /api/subscriptions` exposes no plan ids.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Purchase allowed",
  "data": {
    "allowed": true
  }
}
```

| Field | Type | Description |
|-------|------|--------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data.allowed | boolean | Always `true` in a 200 — denied cases come back as 409 |

The gate has exactly two allowed outcomes:

1. **No row in effect** (FREE / cancelled / expired member) → a first purchase of any purchasable plan is allowed.
2. **Row in effect, same plan, no `scheduled` successor** → a same-plan extension is allowed.

Anything else is a 409 (below); the 409 body itself is the `allowed: false` signal — there is no `200 {allowed: false}` shape.

This endpoint is **read-only**: it never creates, updates, locks-for-write, or deletes a subscription row (feature spec FR-007). Frontends call it before starting checkout; IPN fulfilment re-asserts the identical gate as the authority.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | `planCode` missing or blank | "Validation failed" |
| 400 | Plan exists but is not `ACTIVE` | "Plan is not active" |
| 400 | Plan price ≤ 0 | "Plan is not purchasable: price must be greater than zero" |
| 400 | Plan currency is not VND | "Plan is not purchasable: only VND plans can be purchased" |
| 401 | Missing, expired, or invalid JWT | "Unauthorized" |
| 404 | No `ai_plan` row with that code | "Plan not found" |
| 409 | Row in effect, different plan than requested | "Cancel your current subscription before purchasing a different plan" |
| 409 | Row in effect, same plan, `scheduled` successor already exists | "A renewal is already scheduled for this plan" |
| 500 | Server error | "Internal server error" |

Plan validation (400/404) runs before the lifecycle gate, mirroring `PaymentCheckoutService.validatePurchasable` — a 404/400 says the plan itself is wrong for everyone; a 409 says this member's current subscription state blocks it.

## Business Rules
- BR-SUBS-004 — the one-in-effect-row purchase gate: branch decisions, messages, and no-write guarantee.
- BR-PAY-009 — only active, priced, VND plans are purchasable (the 400/404 checks).
- BR-SUBS-002 — plan limits/prices come from `ai_plan`, never constants.
- Feature spec FR-006 / FR-007 / NFR-MAINT-001: `docs/feats/subscription-lifecycle.md`

## Example

### Request
```bash
curl -X POST "http://localhost:8080/api/subscriptions/me/purchase/eligibility" \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{"planCode":"PRO"}'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Purchase allowed",
  "data": {
    "allowed": true
  }
}
```

### Blocked by an Active Different Plan (409)
```json
{
  "success": false,
  "message": "Cancel your current subscription before purchasing a different plan",
  "data": null
}
```

### Blocked by an Existing Scheduled Extension (409)
```json
{
  "success": false,
  "message": "A renewal is already scheduled for this plan",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/subscription-lifecycle.md`
- Fulfilment authority (re-runs this gate): `GET /api/payments/vnpay/ipn` — `docs/apis/payments/get-vnpay-ipn.md`
- Checkout this gates: `POST /api/payments/checkout` — `docs/apis/payments/post-checkout.md`
- Sibling endpoint: `docs/apis/subscriptions/post-cancel.md` (`POST /api/subscriptions/me/cancel`)
