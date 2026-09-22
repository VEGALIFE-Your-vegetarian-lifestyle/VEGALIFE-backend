# API Reference Template

## When to use
Use this template for **each individual API endpoint**. One file per endpoint.

## File naming convention
`docs/apis/<feature>/<http-method>-<resource-path>.md`

Examples:
- `docs/apis/auth/post-register.md`
- `docs/apis/auth/get-verify-email.md`
- `docs/apis/recipes/get-recipes.md`
- `docs/apis/users/get-profile.md`

Convert path: `/api/auth/register` → `post-register.md`
Convert path: `/api/auth/verify-email` → `get-verify-email.md`

## Index format
In `docs/apis/index.md`, list as:

| File | Endpoint | Description |
|------|----------|-------------|
| `auth/post-register.md` | `POST /api/auth/register` | Register new user |
| `auth/get-verify-email.md` | `GET /api/auth/verify-email` | Verify email with token |

---

# API Reference: <HTTP Method> <Path>

## Overview
<!-- One sentence: what this endpoint does -->

## Endpoint
```
<HTTP_METHOD> /api/<resource-path>
```

## Authentication
<!-- Auth requirement: JWT Bearer, API key, none, etc. -->

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
|      |      |          |             |

### Query Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
|      |      |          |             |

### Request Body
```json
{
  "field": "type — description"
}
```
<!-- If no body, write: "No request body" -->

## Responses

### Success Response (<status code>)
```json
{
  "success": true,
  "message": "string",
  "data": { }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data | object | Response payload |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | Validation failed | "Validation failed" |
| 401 | Unauthorized | "Authentication required" |
| 403 | Forbidden | "Insufficient permissions" |
| 404 | Not found | "Resource not found" |
| 409 | Conflict | "Resource already exists" |
| 500 | Server error | "Internal server error" |

## Business Rules
<!-- Link to relevant business rules: BR-AUTH-001, BR-AUTH-002, etc. -->

## Example

### Request
```bash
curl -X POST /api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"johndoe","email":"john@example.com","password":"securePass123","confirmPassword":"securePass123"}'
```

### Success Response (201)
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com"
  }
}
```

---

## Related
- Feature Spec: `docs/feats/<feature-name>.md`
- ADR: `docs/adrs/<NNNN>-<title>.md`
- Business Rules: `docs/brs/<feature-name>.md`