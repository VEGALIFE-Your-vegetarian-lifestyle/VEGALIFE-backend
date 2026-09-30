# Feature Spec: Edit Category API (Admin)

## Status
Implemented

## Author / owner
Backend team (Huy); tracked in Sprint plan (VEGALIFE-Sprint.xlsx, "Vegalife BE" row 34, item 32.0).

## Summary
Allow an Administrator to edit an existing content category's name and/or description.

## Problem / motivation
`create-category-api.md` (#31.0) lets an Admin create categories, but a category name or description created in error, or one that needs wording changes over time, can't be corrected today. BR-ADMIN-003 requires that only an Administrator can create, edit, retire, or remove categories.

## Goals
- Admin can edit a category's `name` and/or `description` by id.
- Partial update: an omitted or `null` field keeps its current value (same convention as `PATCH /api/posts/{postId}`).
- Editing `name` still enforces case-insensitive uniqueness among active categories (excluding the category being edited).
- Non-admin callers get 403; missing/invalid JWT gets 401.

## Non-goals
- Retire/delete categories (Sprint item 33.0).
- List categories (Sprint item 34.0).
- Clearing `description` back to `null` via an explicit null in the request body — mirrors the existing post-edit convention that a field cannot be nulled out through partial update; only a non-blank value changes it.
- Recording category edits in `moderation_log` (BR-ADMIN-002 scopes moderation logging to content/comment moderation, not category management, matching the decision made for `create-category-api.md`).

## Requirements

### Functional Requirements
- [x] FR-001: `PATCH /api/admin/categories/{categoryId}` requires role ADMIN.
- [x] FR-002: At least one of `name`, `description` must be present in the request body, or 400.
- [x] FR-003: `name`, when provided, is trimmed, non-blank, max 100 characters.
- [x] FR-004: `description`, when provided, is trimmed; a blank value is stored as `null`.
- [x] FR-005: On success, response is 200 with the updated category (`id`, `name`, `description`, `createdAt`).
- [x] FR-006: A `name` that collides case-insensitively with a different active category → 409.
- [x] FR-007: Missing or soft-deleted category id → 404.
- [x] FR-008: Non-admin authenticated → 403; no/invalid JWT → 401 (existing SecurityConfig `/api/admin/**`).

### Non-Functional Requirements
- [x] NFR-DATA-001: Reuses the same case-insensitive partial unique index (`idx_category_active_name_unique`) added for creation — no new schema change needed; a `DataIntegrityViolationException` from a race falls back to the existing generic 409 handler.
- [x] NFR-MAINT-001: Response and error shapes use existing `ApiResponse<T>` and `GlobalExceptionHandler`; no new wrapper styles.

## Design overview
`CategoryService.updateCategory(categoryId, request)` loads the category via `findById` filtered on `deletedAt == null` (404 if absent), applies `name` (trim + uniqueness check excluding self via `existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot`) and `description` (trim, blank → null) only when present in the request, then saves. Controller change is additive to the existing `AdminCategoryController`: a new `PATCH /{categoryId}` mapping alongside the existing `POST`.

## Success metrics
- Sprint item 32.0 ("Edit categories API") delivered; a previously created category's name/description can be corrected without recreating it.

## Acceptance criteria
**As an** admin, **I want to** edit a category's name or description, **so that** I can correct or refine it without losing the category's id or its post associations.

- [x] Given an admin JWT, when editing a category with a new unique name, then 200 with the updated category.
- [x] Given an admin JWT, when editing only `description`, then `name` is unchanged in the response.
- [x] Given an admin JWT, when editing `name` to match another active category (any case), then 409.
- [x] Given an admin JWT, when editing a missing or soft-deleted category, then 404.
- [x] Given an admin JWT, when the request body has neither field, then 400.
- [x] Given a non-admin JWT, when calling the endpoint, then 403.
- [x] Given no JWT, when calling the endpoint, then 401.

## Risks / open questions
- Same open question as `create-category-api.md`: retire/soft-delete semantics (Sprint item 33.0) aren't built yet, so this spec only guards against colliding with other *active* categories.

## Related
- API Reference: `docs/apis/admin/patch-categories-categoryid.md`
- Companion: `docs/feats/create-category-api.md` (creation, same uniqueness constraint)
- Business rule: BR-ADMIN-003 (category creation and maintenance restricted to Admin)
