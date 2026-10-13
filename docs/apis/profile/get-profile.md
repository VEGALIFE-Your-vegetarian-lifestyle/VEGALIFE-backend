# API Reference: GET /api/profile

## Overview
Returns the authenticated user's own profile, creating an empty profile
row on first read if none exists yet.

## Endpoint
```
GET /api/profile
```

## Authentication
JWT Bearer token required (`Authorization: Bearer <accessToken>`).
Returns `401` when the header is missing or invalid.

## Request

### Path Parameters
No path parameters.

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
    "bmi": 22.9,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatarUrl": "https://cdn.example.com/avatar.jpg",
    "preferences": ["spicy", "high-protein"],
    "allergies": ["peanuts"],
    "healthGoal": "weight loss",
    "updatedAt": "2026-10-01T08:15:30Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | Human-readable message |
| data | object | `ProfileResponse` payload (see below) |
| data.userId | UUID | ID of the profile's user |
| data.username | string | The user's username |
| data.email | string | The user's email address |
| data.heightCm | number \| null | Height in cm (precision 5, scale 2); `null` if never set |
| data.weightKg | number \| null | Weight in kg (precision 5, scale 2); `null` if never set |
| data.bmi | number \| null | Computed `weightKg / (heightCm/100)²`, one decimal (HALF_UP). Derived at read time, never stored; omitted when height or weight is missing |
| data.age | integer \| null | Age 1–150; `null` if never set |
| data.gender | string \| null | One of `male`, `female`, `other`; `null` if never set |
| data.description | string \| null | Bio, max 2000 chars; `null` if never set |
| data.avatarUrl | string \| null | Avatar URL; `null` if never set |
| data.preferences | string[] \| absent | Dietary preferences, ≤50 tags of ≤100 chars each |
| data.dislikes | string[] \| absent | Disliked ingredients/dishes, same tag limits |
| data.allergies | string[] \| absent | Allergies (hard constraints), same tag limits |
| data.restrictions | string[] \| absent | Dietary restrictions (hard constraints), same tag limits |
| data.healthGoal | string \| absent | Free-text health goal, ≤200 chars |
| data.updatedAt | string | ISO-8601 timestamp of the profile row (DB default `NOW()`, never `null`) |

The five dietary fields are stored together as one JSONB value
(`user_profile.dietary_preferences`) but appear on the wire as flat
top-level members. Unset fields and a `null` BMI are omitted from the JSON
entirely.

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 401 | Missing/invalid JWT | "Authentication required" |
| 500 | Server error | "Internal server error" |

## Business Rules
- BR-PROFILE-003 (Profile Auto-Creation) — extended by this endpoint: the
  row is created on first read, not only on first write
  (`docs/brs/profile.md`).

## Example

### Request
```bash
curl -X GET /api/profile \
  -H "Authorization: Bearer <accessToken>"
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
    "heightCm": null,
    "weightKg": null,
    "age": null,
    "gender": null,
    "description": null,
    "avatarUrl": null,
    "updatedAt": null
  }
}
```

---

## Related
- Feature Spec: `docs/feats/get-user-profile-api.md`
- Business Rules: `docs/brs/profile.md`
- Related endpoint: `docs/apis/profile/get-profile-userid.md`
