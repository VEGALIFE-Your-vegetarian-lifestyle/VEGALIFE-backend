# API Reference: <service/module name>

## Overview

<!-- One or two sentences: what this API is for, who calls it. -->

## Base path / versioning

<!-- e.g. `/api/v1`. Note the versioning scheme if one exists (URL
version, header version, none yet). -->

## Authentication

<!-- What every caller needs: token type, header name, scopes/roles
required. If different endpoints need different auth, note it per
endpoint below instead of here. -->

## Endpoints

<!-- Repeat this block per endpoint. Keep request/response fields to
what's actually returned/accepted — don't document a hypothetical
"could be added later" field. -->

### `<METHOD>` `<path>`

**Description**: <what this endpoint does, one sentence>

**Auth**: <required role/scope, or "none" / "inherits base auth">

**Request**:
- Path params: <name: type — description>
- Query params: <name: type — description, required/optional>
- Body:
  ```json
  {}
  ```

**Response** (`<status code>`):
```json
{}
```

**Error responses**:
| Status | Condition |
|---|---|
| | |

## Relevant project conventions

<!-- TODO: fill in once the repo is inspected — pagination scheme,
error response shape used consistently across the API, rate limits. -->

---

## Example

# API Reference: Orders Service

## Overview
Endpoints for creating, viewing, and cancelling customer orders.

## Base path / versioning
`/api/v1/orders` — versioned via URL path; breaking changes go to `/v2`.

## Authentication
All endpoints require a `Bearer` token in the `Authorization` header.
Cancel requires the token's user to own the order or hold the `support`
role.

## Endpoints

### `POST` `/api/v1/orders/{orderId}/cancel`

**Description**: Cancels an order if it's still within the
cancellation window.

**Auth**: order owner, or `support` role

**Request**:
- Path params: `orderId: string` — the order to cancel
- Body:
  ```json
  { "reason": "string (optional)" }
  ```

**Response** (`200`):
```json
{ "orderId": "string", "status": "cancelled", "refundId": "string | null" }
```

**Error responses**:
| Status | Condition |
|---|---|
| 404 | Order not found or not owned by caller |
| 409 | Order is outside the 24-hour cancellation window |
| 409 | Order already cancelled or already shipped |
