# ADR-001: User Registration Architecture Decisions

## Status

Accepted

## Context

The Vegalife backend requires a user registration system with email verification. This ADR documents the key architectural decisions made during implementation.

## Decisions

### 1. Service Layer: AuthService over UserService

**Decision**: Rename `UserService` → `AuthService` and place in `service/auth` package.

**Rationale**: 
- Registration and email verification are authentication/authorization concerns, not general user management
- Separates auth flows from future user profile management
- Clearer domain boundary: `service/auth` for auth, `service/user` for profile (future)

**Consequences**:
- Controllers depend on `AuthService`
- Tests updated accordingly
- Future user profile features go in separate `UserService`

### 2. Mapper Strategy: Feature-Specific MapStruct Mappers

**Decision**: Create `AuthMapper` in `dto/mapper/auth/` instead of generic `UserMapper`.

**Rationale**:
- Avoids generic `GenericMapper<E, R, C>` abstraction
- Each feature/domain gets its own mapper with only needed methods
- `AuthMapper` handles: `RegisterRequest + encodedPassword → User` and `User → AuthResponse`
- No `INSTANCE` field - uses `@Mapper(componentModel = "spring")` for Spring injection
- Business logic (password encoding, defaults) stays in service, not mapper

**Consequences**:
- Clear ownership: auth mappings in `AuthMapper`
- Easy to add `UserMapper` later for profile endpoints
- MapStruct generates implementation at compile time

### 3. API Response Envelope: ApiResponse<T>

**Decision**: Wrap all responses in `ApiResponse<T>` with fields: `success`, `message`, `data`.

**Rationale**:
- Consistent API contract across all endpoints
- Frontend can handle success/error uniformly
- HTTP status in `ResponseEntity`, business status in `success` boolean
- Factory methods: `ApiResponse.success(data, message)` and `ApiResponse.failure(message)`

**Consequences**:
- All controllers return `ResponseEntity<ApiResponse<T>>`
- GlobalExceptionHandler returns `ApiResponse.failure()` with appropriate HTTP status
- Removed `message` field from `AuthResponse` (moved to envelope)

### 4. Validation: Generic FieldsEqual Annotation

**Decision**: Replace password-specific `PasswordMatch` with generic `FieldsEqual` supporting 2+ fields.

**Rationale**:
- Reusable across any DTO needing field equality (e.g., newPassword/confirmNewPassword)
- Class-level annotation (`@Target(ElementType.TYPE)`) per Jakarta Validation spec
- Array-based: `@FieldsEqual({"field1", "field2", "field3"})`

**Consequences**:
- Removed `PasswordMatch` and `PasswordMatchValidator`
- `RegisterRequest` uses `@FieldsEqual({"password", "confirmPassword"})`

### 5. HTTP Status Codes

**Decision**: 
- `POST /register` → 201 Created (resource created)
- `GET /verify-email` → 200 OK (verification is idempotent read)
- Errors: 400, 404, 409, 500 as appropriate

**Rationale**: REST semantics - creation returns 201, verification is read-like

### 6. Exception Handling: GlobalExceptionHandler Returns ApiResponse

**Decision**: `GlobalExceptionHandler` returns `ResponseEntity<ApiResponse<?>>` using `ApiResponse.failure()`.

**Rationale**:
- Single response format for success and error
- Error message in `message` field, `success: false`, `data: null`
- Validation errors could include field errors in `data` (future enhancement)

### 7. Test Structure: Unit + Integration with Profiles

**Decision**: 
- Unit tests: `@WebMvcTest`, `@ExtendWith(MockitoExtension)`, H2 in-memory
- Integration tests: `@SpringBootTest`, Testcontainers PostgreSQL, `@ActiveProfiles("integration")`
- Separate source folders: `unit/` and `integration/`

**Rationale**:
- Fast unit tests for logic, slower integration tests for DB interactions
- Testcontainers provides production-like PostgreSQL
- Profile-based configuration isolation

## Alternatives Considered

| Decision | Alternative | Rejected Because |
|----------|-------------|------------------|
| AuthService | Keep UserService | Auth concerns mixed with user management |
| AuthMapper | Generic Mapper interface | Over-abstraction, violates YAGNI |
| ApiResponse<T> | Raw DTO responses | Inconsistent API, no standard envelope |
| FieldsEqual | Keep PasswordMatch | Not reusable, password-specific |
| 201 on register | 200 OK | Not semantically correct for creation |

## Related

- Feature Spec: `docs/feats/user-registration.md`
- API Docs: `docs/apis/auth-endpoints.md`