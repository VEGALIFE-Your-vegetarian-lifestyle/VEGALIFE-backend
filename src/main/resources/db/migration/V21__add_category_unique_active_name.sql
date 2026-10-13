-- V21__add_category_unique_active_name.sql
-- BR-ADMIN-003: category names must be unique among active (non-deleted) categories

CREATE UNIQUE INDEX idx_category_active_name_unique ON category (lower(name)) WHERE deleted_at IS NULL;
