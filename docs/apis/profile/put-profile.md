# API Reference: PUT /api/profile

## Overview
Update the authenticated user's profile with physical metrics and description. Creates profile if it doesn't exist.

## Endpoint
```
PUT /api/profile
```

## Authentication
Required: Valid JWT access token in Authorization header (`Bearer <token>`)

## Request

### Request Body
```json
{
  "height_cm": "number — optional, positive decimal, max 300",
  "weight_kg": "number — optional, positive decimal, max 500",
  "age": "integer — optional, between 1 and 150",
  "gender": "string — optional, one of: male, female, other",
  "description": "string — optional, max 2000 characters",
  "avatar_url": "string — optional, valid URL format"
}
```
At least one field must be provided.

## Responses

### Success Response (200 OK)
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": {
    "id": "uuid",
    "user_id": "uuid",
    "height_cm": 175.5,
    "weight_kg": 70.2,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatar_url": "https://example.com/avatar.jpg",
    "updated_at": "2026-09-23T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | "Profile updated successfully" |
| data.id | uuid | Unique profile identifier |
| data.user_id | uuid | Associated user identifier |
| data.height_cm | number | Height in centimeters |
| data.weight_kg | number | Weight in kilograms |
| data.age | integer | User age |
| data.gender | string | male, female, or other |
| data.description | string | User description |
| data.avatar_url | string | Profile avatar URL |
| data.updated_at | string | ISO 8601 timestamp of last update |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | No fields provided | "At least one profile field must be provided" |
| 400 | Invalid height_cm | "Height must be between 0 and 300 cm" |
| 400 | Invalid weight_kg | "Weight must be between 0 and 500 kg" |
| 400 | Invalid age | "Age must be between 1 and 150" |
| 400 | Invalid gender | "Gender must be male, female, or other" |
| 400 | Description too long | "Description must not exceed 2000 characters" |
| 400 | Invalid avatar_url | "Avatar URL must be a valid URL" |
| 401 | Missing/invalid JWT | "Invalid or missing access token" |
| 500 | Server error | "Internal server error" |

## Validation Rules
- **height_cm**: Optional, positive decimal, maximum 300 (BR-PROFILE-001)
- **weight_kg**: Optional, positive decimal, maximum 500 (BR-PROFILE-001)
- **age**: Optional, integer between 1 and 150 (BR-PROFILE-001)
- **gender**: Optional, must be one of: male, female, other (BR-PROFILE-001)
- **description**: Optional, maximum 2000 characters
- **avatar_url**: Optional, valid URL format (RFC 3986)

## Business Rules
- BR-PROFILE-001: Profile Fields Validation
- BR-PROFILE-002: Profile Ownership (users can only update their own profile)
- BR-PROFILE-003: Profile Auto-Creation (create profile if not exists on first update)

## Flow
1. Extract userId from JWT token
2. Validate request body (at least one field, all field constraints)
3. Find or create UserProfile for userId
4. Update provided fields
5. Save profile
6. Return 200 with updated profile data

## Example

### Request
```bash
curl -X PUT http://localhost:8080/api/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "height_cm": 175.5,
    "weight_kg": 70.2,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatar_url": "https://example.com/avatar.jpg"
  }'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "height_cm": 175.5,
    "weight_kg": 70.2,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatar_url": "https://example.com/avatar.jpg",
    "updated_at": "2026-09-23T10:00:00Z"
  }
}
```

### Error Response (400 - Validation)
```json
{
  "success": false,
  "message": "Validation failed",
  "data": null
}
```

### Error Response (401 - Unauthorized)
```json
{
  "success": false,
  "message": "Invalid or missing access token",
  "data": null
}
```

## Related
- Feature Spec: `docs/feats/edit-user-profile-api.md`
- Business Rules: `docs/brs/auth.md` (for JWT authentication rules)