# API Reference: GET /api/profile/{userId}

## Overview
Returns any member's public profile by user ID; requires no
authentication. Creates an empty profile row on first read if the user has
none yet.

## Endpoint
```
GET /api/profile/{userId}
```

## Authentication
None — public endpoint (`permitAll` in `SecurityConfig`). The profile
response includes the member's `email`; see the feature spec's risks
section for the accepted trade-off.

## Request

### Path Parameters
| Name | Type | Required | Description |
|------|------|----------|-------------|
| userId | UUID | Yes | ID of the user whose profile is read |

### Query Parameters
No query parameters.

### Request Body
No request body.

## Responses

### Success Response (200)
```json
{
  "success": true,
  "message": "Profile retrieved successfully",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com",
    "heightCm": 175.50,
    "weightKg": 70.20,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatarUrl": "https://cdn.example.com/avatar.jpg",
    "updatedAt": "2026-10-01T08:15:30Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data | object | `ProfileResponse` payload — identical shape to `GET /api/profile` |
| data.userId | UUID | ID of the requested user |
| data.username | string | The user's username |
| data.email | string | The user's email address |
| data.heightCm | number \| null | Height in cm; `null` if never set |
| data.weightKg | number \| null | Weight in kg; `null` if never set |
| data.age | integer \| null | Age 1–150; `null` if never set |
| data.gender | string \| null | One of `male`, `female`, `other`; `null` if never set |
| data.description | string \| null | Bio, max 2000 chars; `null` if never set |
| data.avatarUrl | string \| null | Avatar URL; `null` if never set |
| data.updatedAt | string | ISO-8601 timestamp of the profile row (DB default `NOW()`, never `null`) |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 404 | `userId` does not exist | "User not found" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-PROFILE-003 (Profile Auto-Creation) — the row is created on first
  read, not only on first write (`docs/brs/profile.md`).
- Public visibility is unconditional; there is no per-user privacy toggle.

## Example

### Request
```bash
curl -X GET /api/profile/550e8400-e29b-41d4-a716-446655440000
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Profile retrieved successfully",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com",
    "heightCm": 175.50,
    "weightKg": 70.20,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatarUrl": "https://cdn.example.com/avatar.jpg",
    "updatedAt": "2026-10-01T08:15:30Z"
  }
}
```

### Error Response (404)
```json
{
  "success": false,
  "message": "User not found",
  "data": null
}
```

---

## Related
- Feature Spec: `docs/feats/get-user-profile-api.md`
- Business Rules: `docs/brs/profile.md`
- Own-profile variant: `docs/apis/profile/get-profile.md`
