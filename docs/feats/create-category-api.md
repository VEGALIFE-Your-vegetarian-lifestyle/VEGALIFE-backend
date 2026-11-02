# Feature Spec: Create Category API (Admin)

## Status
Implemented

## Author / owner
Backend team (Huy); tracked in Sprint plan (VEGALIFE-Sprint.xlsx, "Vegalife BE" row 33, item 31.0).

## Summary
Allow an Administrator to create a new content category (name + optional description) so posts can later be classified against it.

## Problem / motivation
Categories already exist as a data model (`Category` entity, `category` table) and are consumed when creating/publishing posts (`PostService.resolveCategories`, BR-CONTENT-004), but nothing can create them yet — the table starts empty with no way to populate it. BR-ADMIN-003 requires that only an Administrator can create, edit, retire, or remove categories.

## Goals
- Admin can create a category by name, with an optional description.
- Category names are unique among active (non-deleted) categories, case-insensitively.
- Non-admin callers get 403; missing/invalid JWT gets 401.

## Non-goals
- Edit, retire/delete, or list categories (separate Sprint items 32.0–34.0).
- Category hierarchy/parent-child relationships.
- Bulk create.
- Recording category creation in `moderation_log` (BR-ADMIN-002 scopes moderation logging to content/comment moderation, not category management).

## Requirements

### Functional Requirements
- [x] FR-001: `POST /api/admin/categories` requires role ADMIN.
- [x] FR-002: `name` is required, trimmed, max 100 characters.
- [x] FR-003: `description` is optional; blank values are stored as `null`.
- [x] FR-004: On success, response is 201 with the created category (`id`, `name`, `description`, `createdAt`).
- [x] FR-005: A name that collides case-insensitively with an existing active category → 409.
- [x] FR-006: Missing/blank name or name over 100 characters → 400.
- [x] FR-007: Non-admin authenticated → 403; no/invalid JWT → 401 (existing SecurityConfig `/api/admin/**`).

### Non-Functional Requirements
- [x] NFR-DATA-001: Uniqueness is enforced at the database level (partial unique index on `lower(name)` where `deleted_at IS NULL`), not only in application code, so concurrent requests cannot create duplicate active names.
- [x] NFR-MAINT-001: Response and error shapes use existing `ApiResponse<T>` and `GlobalExceptionHandler`; no new wrapper styles.

## Design overview
New `CategoryService.createCategory` trims the requested name, checks `CategoryRepository.existsByNameIgnoreCaseAndDeletedAtIsNull` for an application-level 409 (fast path, friendlier message), then saves. A new Flyway migration (`V21`) adds a case-insensitive partial unique index on `category(lower(name)) WHERE deleted_at IS NULL` as the authoritative constraint; a `DataIntegrityViolationException` from a race would surface as the existing generic 409 handler in `GlobalExceptionHandler`.

New controller `AdminCategoryController` at `/api/admin/categories`, alongside the existing `AdminController`, since it shares the same role restriction and path prefix but is a distinct resource.

## Success metrics
- Sprint item 31.0 ("Create categories API") delivered; categories can subsequently be referenced by `categoryIds` when creating/publishing posts.

## Acceptance criteria
**As an** admin, **I want to** create a content category, **so that** posts can be organized and filtered by category.

- [x] Given an admin JWT, when creating a category with a unique name, then 201 with the created category.
- [x] Given an admin JWT, when creating a category whose name matches an existing active category (any case), then 409.
- [x] Given an admin JWT, when creating a category with a blank name, then 400.
- [x] Given a non-admin JWT, when calling the endpoint, then 403.
- [x] Given no JWT, when calling the endpoint, then 401.

## Risks / open questions
- No reuse-of-retired-name behavior is defined yet since retire/delete (item 33.0) isn't built — a soft-deleted category's name is currently free to reuse, which matches the partial unique index scope (`WHERE deleted_at IS NULL`) and should be revisited if retire semantics change.

## Related
- API Reference: `docs/apis/admin/post-categories.md`
- Data dictionary: `docs/arch/data-dictionary.md` (Table 3: Category)
- Business rule: BR-ADMIN-003 (category creation and maintenance restricted to Admin)
- Consumer: `docs/apis/post/post-posts.md` (categoryIds)
