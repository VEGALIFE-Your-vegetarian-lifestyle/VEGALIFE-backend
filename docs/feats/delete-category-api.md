# Feature Spec: Delete Category API (Admin)

## Status
Implemented

## Author / owner
Backend team (Huy); tracked in Sprint plan (VEGALIFE-Sprint.xlsx, "Vegalife BE" row 35, item 33.0).

## Summary
Allow an Administrator to remove a content category from future use, without breaking posts that already reference it.

## Problem / motivation
BR-ADMIN-003 states: "A category that is still used by existing content must not be deletable outright — it can only be retired from future use, so existing content is not broken." A category row is referenced by `post_category` (`ON DELETE CASCADE`); a true row delete would silently strip the category from every post that used it. This feature never hard-deletes the row — it only "retires" it, the same soft-delete convention already used for posts (`docs/apis/post/delete-posts-postid.md`) and users.

## Goals
- Admin can retire a category by id; `deleted_at` is set and the row is kept.
- Existing posts that reference the category keep that reference untouched.
- A retired category can no longer be assigned to new/edited posts (already enforced by `PostService.resolveCategories`, which only resolves against `deletedAt IS NULL` categories — BR-CONTENT-004).
- Retiring is idempotent-safe: retiring an already-retired (or missing) category returns 404, matching the existing delete-post convention.
- Non-admin callers get 403; missing/invalid JWT gets 401.

## Non-goals
- Hard/permanent delete of a category row (never done, regardless of whether it's referenced — this is what BR-ADMIN-003 forbids).
- Un-retiring / restoring a category (no Sprint item for this yet).
- List categories (Sprint item 34.0).
- Blocking retirement based on whether the category is currently in use — retirement is always allowed and is always non-destructive to existing content, so no usage check is needed before allowing it.
- Recording category retirement in `moderation_log` (BR-ADMIN-002 scopes moderation logging to content/comment moderation; same decision made for create/edit category APIs).

## Requirements

### Functional Requirements
- [x] FR-001: `DELETE /api/admin/categories/{categoryId}` requires role ADMIN.
- [x] FR-002: On success, the category's `deleted_at` is set to the current time; the row and its `post_category` links are kept.
- [x] FR-003: Response is 200 with `data: null`.
- [x] FR-004: Missing or already-retired category id → 404.
- [x] FR-005: Non-admin authenticated → 403; no/invalid JWT → 401 (existing SecurityConfig `/api/admin/**`).
- [x] FR-006: A post that already references the retired category keeps that category in its `categoryIds`/`categories` after retirement.

### Non-Functional Requirements
- [x] NFR-DATA-001: No hard delete is ever issued against `category`, so the `post_category` foreign key is never exercised — no risk of the `ON DELETE CASCADE` silently stripping categories from posts.
- [x] NFR-MAINT-001: Response and error shapes use existing `ApiResponse<T>` and `GlobalExceptionHandler`; no new wrapper styles.

## Design overview
`CategoryService.deleteCategory(categoryId)` reuses the existing `findActiveCategory` helper (404 if missing/already retired), sets `deletedAt = Instant.now()`, and saves — the same shape as `PostService.deletePost`. No new migration: uses the `category.deleted_at` column already added in `V2__create_category_table.sql`. Controller adds `DELETE /{categoryId}` to the existing `AdminCategoryController`.

## Success metrics
- Sprint item 33.0 ("Delete categories API") delivered; retiring a category never removes it from posts that already reference it.

## Acceptance criteria
**As an** admin, **I want to** retire a category that's no longer needed, **so that** it stops being offered for new content while existing posts keep working.

- [x] Given an admin JWT, when retiring an active category, then 200 with `data: null` and `deletedAt` set.
- [x] Given an admin JWT, when retiring a category still referenced by an existing post, then 200 and the post's category link is unaffected.
- [x] Given an admin JWT, when retiring a missing category, then 404.
- [x] Given an admin JWT, when retiring an already-retired category, then 404.
- [x] Given a non-admin JWT, when calling the endpoint, then 403.
- [x] Given no JWT, when calling the endpoint, then 401.

## Related
- API Reference: `docs/apis/admin/delete-categories-categoryid.md`
- Companion: `docs/feats/create-category-api.md`, `docs/feats/edit-category-api.md`
- Business rule: BR-ADMIN-003 (category creation and maintenance restricted to Admin; retire not delete)
