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
  "heightCm": "number — optional, positive decimal, max 300",
  "weightKg": "number — optional, positive decimal, max 500",
  "age": "integer — optional, between 1 and 150",
  "gender": "string — optional, one of: male, female, other",
  "description": "string — optional, max 2000 characters",
  "avatarUrl": "string — optional, valid URL format"
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
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com",
    "heightCm": 175.5,
    "weightKg": 70.2,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatarUrl": "https://example.com/avatar.jpg",
    "updatedAt": "2026-09-23T10:00:00Z"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Always true for success |
| message | string | "Profile updated successfully" |
| data | object | `ProfileResponse` — identical shape to both GET endpoints |
| data.userId | uuid | Associated user identifier |
| data.username | string | The user's username |
| data.email | string | The user's email address |
| data.heightCm | number | Height in centimeters |
| data.weightKg | number | Weight in kilograms |
| data.age | integer | User age |
| data.gender | string | male, female, or other |
| data.description | string | User description |
| data.avatarUrl | string | Profile avatar URL |
| data.updatedAt | string | ISO 8601 timestamp of last update |

### Error Responses
| Status Code | Condition | Message |
|-------------|-----------|---------|
| 400 | No fields provided | "At least one profile field must be provided" |
| 400 | Invalid heightCm | "Height must be between 0 and 300 cm" |
| 400 | Invalid weightKg | "Weight must be between 0 and 500 kg" |
| 400 | Invalid age | "Age must be between 1 and 150" |
| 400 | Invalid gender | "Gender must be male, female, or other" |
| 400 | Description too long | "Description must not exceed 2000 characters" |
| 400 | Invalid avatarUrl | "Avatar URL must be a valid URL" |
| 401 | Missing/invalid JWT | "Invalid or missing access token" |
| 404 | User not found | "User not found" |
| 500 | Server error | "Internal server error" |

## Validation Rules
- **heightCm**: Optional, positive decimal, maximum 300 (BR-PROFILE-001)
- **weightKg**: Optional, positive decimal, maximum 500 (BR-PROFILE-001)
- **age**: Optional, integer between 1 and 150 (BR-PROFILE-001)
- **gender**: Optional, must be one of: male, female, other (BR-PROFILE-001)
- **description**: Optional, maximum 2000 characters
- **avatarUrl**: Optional, valid URL format (RFC 3986)

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
    "heightCm": 175.5,
    "weightKg": 70.2,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatarUrl": "https://example.com/avatar.jpg"
  }'
```

### Success Response (200)
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "johndoe",
    "email": "john@example.com",
    "heightCm": 175.5,
    "weightKg": 70.2,
    "age": 25,
    "gender": "male",
    "description": "Vegan enthusiast",
    "avatarUrl": "https://example.com/avatar.jpg",
    "updatedAt": "2026-09-23T10:00:00Z"
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
- Feature Spec: `docs/feats/edit-user-profile-api.md`, `docs/feats/get-user-profile-api.md`
- Business Rules: `docs/brs/profile.md`; JWT rules in `docs/brs/auth.md`
